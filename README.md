# Vertx-Mongo-API
This project demonstrates the use of Vertx verticles to implement basic CRUD functionality.
  It relies on a MongoDB backend.

## Necessary components
* a config file, src/main/java/resourcesconf/config.json, such as the following:
    * "mongodb.username", "mongodb.password" and "mongodb.authsource" may be ommitted if the database does not use authorization ( **play at your own risk** )
```json
{
  "http.port": 8080,
  "server.passphrase": "some AuthToken credential",
  "mongodb.host": "host address",
  "mongodb.databasename": "some database name",
  "mongodb.port": 27017,
  "mongodb.username": "predefined username",
  "mongodb.password": "predefined password",
  "mongodb.authSource": "auth database"
}
```
* MongoDB setup locally, or a cloud-provided account
* Java - Version 11 installed (I believe this would run ok on version 8 or above, but changes may need to be made in the pom.xml file for this to work, particularly in the `maven-compiler-source` and `maven-compiler-target` tags)
* An IDE to run in (I use IntelliJ, but Eclipse would also work)
* Something to post CRUD messages to (I use *Postman*)

## Url endpoints
__NOTE: All api endpoints require the following header info:__
```json
    {
      "AuthToken": "<server.passphrase value from config.json>"
    }
```
    
* GET api/v1/products (gets list of all products)
* GET api/v1/products/{:id} (get single product by id)
* POST api/v1/products (Create a product)
    * requires following body content:
    ```json
    {
      "number": "some number (as-string)",
      "description": "some description"
    }
    ```
* PUT api/v1/products/{:id}
    * requires following body content:
    ```json
        {
          "number": "some updated number (as-string)",
          "description": "some updated description"
        }
    ```
* DELETE api/v1/products/{:id}

 This repository is the result of a participating in a __Udemy__ tutorial, '__Vert.x 3.5 Java API's Fast and Simple__' by __Tom Jay__