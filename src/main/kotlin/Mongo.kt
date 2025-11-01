package com.faigenbloom


import com.faigenbloom.models.Item
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.ServerApi
import com.mongodb.ServerApiVersion
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.Document


class Mongo {
    private val database: MongoDatabase
    private val collection: MongoCollection<Item>


    init {
        val connectionString =
            "mongodb+srv://baskinaerobins:lGMil0GKkaDgqzkX@cluster0.grenyrd.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"

        val serverApi = ServerApi.builder()
            .version(ServerApiVersion.V1)
            .build()
        val mongoClientSettings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))

            .serverApi(serverApi)
            .build()

        val mongoClient = MongoClient.create(mongoClientSettings)

        database = mongoClient.getDatabase("famillybudget")
        collection = database.getCollection(Item.COLLECTION_NAME, Item::class.java)

        runBlocking {
            database.runCommand(Document("ping", 1))

            println("//////////////////////////////////////////////////////////////////////////////////////////")
            println("///////////   Pinged your deployment. You successfully connected to MongoDB!   ///////////")
            println("//////////////////////////////////////////////////////////////////////////////////////////")
        }
    }

    suspend fun addItem(item: Item) {
        collection.insertOne(item)
    }

    suspend fun getItems(): List<Item> {
        return collection.find().toList()
    }

    suspend fun markAsDone(id: String, isDone: Boolean) {
        collection.updateOne(
            filter = Filters.eq("id", id),
            update = Updates.set("isDone", isDone)
        )
    }
}