package dev.vihang.neo4jstore.error

sealed class StoreError(val type: String,
                        val id: String,
                        val message: String)

class NotFoundError(type: String,
                    id: String) :
        StoreError(type = type,
                id = id,
                message = "$type - $id not found.")

class AlreadyExistsError(type: String,
                         id: String) :
        StoreError(
                type = type,
                id = id,
                message = "$type - $id already exists.")

class NotCreatedError(type: String,
                      id: String = "",
                      val expectedCount: Int = 1,
                      val actualCount: Int = 0) :
        StoreError(
                type = type,
                id = id,
                message = "Failed to create $type - $id")

class NotUpdatedError(type: String,
                      id: String) :
        StoreError(type = type,
                id = id,
                message = "$type - $id not updated.")

class NotDeletedError(type: String,
                      id: String) :
        StoreError(type = type,
                id = id,
                message = "$type - $id not deleted.")

class GenericStoreError(type: String, id: String, message: String) : StoreError(type, id, message)