package com.faigenbloom.spartaculous.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import com.google.firebase.cloud.StorageClient
import java.util.UUID

class FirebaseService {
    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            if (FirebaseApp.getApps().isEmpty()) {
                val creds = loadCredentials()
                val optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(creds)
                val pid = System.getenv("FIREBASE_PROJECT_ID")
                if (!pid.isNullOrBlank()) optionsBuilder.setProjectId(pid)
                val bucket = System.getenv("FIREBASE_STORAGE_BUCKET")
                    ?: (pid?.let { "$it.appspot.com" })
                if (!bucket.isNullOrBlank()) optionsBuilder.setStorageBucket(bucket)
                val options = optionsBuilder.build()
                FirebaseApp.initializeApp(options)
            }
            initialized = true
        }
    }

    private fun loadCredentials(): GoogleCredentials {
        // Prefer explicit env variables
        val json = System.getenv("FIREBASE_CREDENTIALS")
        if (!json.isNullOrBlank()) {
            return GoogleCredentials.fromStream(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))
        }
        val path = System.getenv("FIREBASE_CREDENTIALS_FILE")
        if (!path.isNullOrBlank()) {
            val file = File(path)
            require(file.exists()) { "FIREBASE_CREDENTIALS_FILE not found: $path" }
            return GoogleCredentials.fromStream(file.inputStream())
        }
        // Fallback: ADC (GCE/GKE/Cloud Run or local gcloud)
        return GoogleCredentials.getApplicationDefault()
    }

    /**
     * Verifies Firebase ID token and returns UID. Tries Admin SDK first (if credentials available),
     * otherwise performs keyless verification using Google's public certs.
     */
    fun verifyAndGetUid(idToken: String): String {
        // Try Admin SDK if possible
        try {
            ensureInit()
            val decoded: FirebaseToken = FirebaseAuth.getInstance().verifyIdToken(idToken)
            return decoded.uid
        } catch (_: Throwable) {
            // fallback to keyless
        }
        return verifyKeylessAndGetUid(idToken)
    }

    private val certsUrl = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
    private val projectId: String = System.getenv("FIREBASE_PROJECT_ID") ?: "spartaculous-da4a8"

    private data class CachedKey(val key: PublicKey, val expiresAtEpochSec: Long)
    private val keyCache = ConcurrentHashMap<String, CachedKey>()
    @Volatile private var cacheExpiresAt: Long = 0L

    private fun verifyKeylessAndGetUid(idToken: String): String {
        val decodedHeader = JWT.decode(idToken).header
        val headerJson = Json.parseToJsonElement(decodedHeader).jsonObject
        val kid = headerJson["kid"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Invalid token header: missing kid")

        val now = Instant.now().epochSecond
        val pubKey = getPublicKey(kid, now)
        val issuer = "https://securetoken.google.com/$projectId"
        val algorithm = Algorithm.RSA256(pubKey as java.security.interfaces.RSAPublicKey, null)
        val verifier = JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(projectId)
            .acceptLeeway(60) // allow small clock skew
            .build()
        val jwt = try {
            verifier.verify(idToken)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Firebase token: ${e.message}")
        }
        val uid = jwt.subject ?: throw IllegalArgumentException("Invalid token: missing subject")
        if (uid.isBlank()) throw IllegalArgumentException("Invalid token: empty subject")
        return uid
    }

    private fun getPublicKey(kid: String, nowEpochSec: Long): PublicKey {
        val cached = keyCache[kid]
        if (cached != null && nowEpochSec < cached.expiresAtEpochSec) return cached.key

        // Refresh all certs if global cache expired
        if (nowEpochSec >= cacheExpiresAt) {
            refreshCerts()
        }
        return keyCache[kid]?.key
            ?: throw IllegalArgumentException("Unknown key id (kid); try again later")
    }

    private fun refreshCerts() {
        val conn = URL(certsUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        if (code != 200) throw IllegalStateException("Failed to fetch Google certs: HTTP $code")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = Json.parseToJsonElement(body).jsonObject

        val maxAge = conn.getHeaderField("Cache-Control")
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("max-age=") }
            ?.substringAfter('=')
            ?.toLongOrNull() ?: 3600L
        val expiresAt = Instant.now().epochSecond + maxAge

        val cf = CertificateFactory.getInstance("X.509")
        val newCache = ConcurrentHashMap<String, CachedKey>()
        for ((k, v) in json) {
            val pem = v.jsonPrimitive.content
            val cert = cf.generateCertificate(pem.byteInputStream()) as X509Certificate
            newCache[k] = CachedKey(cert.publicKey, expiresAt)
        }
        keyCache.clear()
        keyCache.putAll(newCache)
        cacheExpiresAt = expiresAt
    }

    // Placeholder: file uploads can be implemented later (Storage)
    suspend fun uploadFile(
        fileBytes: ByteArray,
        fileName: String,
        contentType: String
    ): String {
        throw NotImplementedError("Firebase Storage upload not configured in this project")
    }

    data class UploadResult(val gsUri: String, val downloadUrl: String)

    /** Uploads bytes to Firebase Storage under users/{uid}/... and returns the object path (gs://bucket/path) */
    fun uploadToFirebaseStorage(uid: String, bytes: ByteArray, originalName: String, contentType: String): String {
        val res = uploadToFirebaseStorageWithUrl(uid, bytes, originalName, contentType)
        return res.gsUri
    }

    /** Same as above, but also returns a Firebase download URL (token-based) suitable for direct HTTPS download. */
    fun uploadToFirebaseStorageWithUrl(uid: String, bytes: ByteArray, originalName: String, contentType: String): UploadResult {
        // Require Admin SDK init (needs credentials). If not available, signal to caller.
        try {
            ensureInit()
        } catch (_: Throwable) {
            throw NotImplementedError("Firebase Storage not configured (no Admin credentials)")
        }
        val app = try { FirebaseApp.getInstance() } catch (e: IllegalStateException) {
            throw NotImplementedError("Firebase Storage not configured (no Admin app)")
        }
        // Resolve bucket name without calling buckets.get
        val bucketName = System.getenv("FIREBASE_STORAGE_BUCKET")
            ?: app.options.storageBucket
            ?: throw IllegalStateException("Firebase Storage bucket not configured")
        val safeExt = originalName.substringAfterLast('.', "").lowercase()
        val fileId = UUID.randomUUID().toString().replace("-", "")
        val objectName = buildString {
            append("users/")
            append(uid)
            append('/')
            append(fileId)
            if (safeExt.isNotEmpty()) {
                append('.')
                append(safeExt)
            }
        }
        // Prepare metadata with Firebase download token so we can build a direct HTTPS URL
        val downloadToken = UUID.randomUUID().toString()
        val metadata = mapOf("firebaseStorageDownloadTokens" to downloadToken)

        val storage = com.google.cloud.storage.StorageOptions.newBuilder()
            .setCredentials(loadCredentials())
            .build()
            .service
        val blobInfo = com.google.cloud.storage.BlobInfo.newBuilder(bucketName, objectName)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()
        val blob = try {
            storage.create(blobInfo, bytes)
        } catch (e: Exception) {
            throw IllegalStateException("Upload to Firebase Storage failed: ${e.message}")
        }
        val gsUri = "gs://$bucketName/${blob.name}"
        val encoded = URLEncoder.encode(blob.name, Charsets.UTF_8)
        val downloadUrl = "https://firebasestorage.googleapis.com/v0/b/$bucketName/o/$encoded?alt=media&token=$downloadToken"
        return UploadResult(gsUri = gsUri, downloadUrl = downloadUrl)
    }
}
