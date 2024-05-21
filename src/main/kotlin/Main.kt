import java.io.BufferedReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.*

fun main(args: Array<String>) {
    val socket = 1337
    lateinit var balancer: Socket
    val balancerIP = "192.168.0.107"
    val balancerPort = 12347
    val serverLock = Any()
    var servers = mutableListOf<Server>()
    var clients = mutableListOf<Client>()
    val clientLock = Any()

    try {
        val balancer = Socket(balancerIP, balancerPort)
        println("Connecting to balancer")

        val input = BufferedReader(balancer.getInputStream().reader())
        val output = PrintWriter(balancer.getOutputStream(), true)

        output.println("cf//socket:$socket")

        var response = input.readLine()

        print("Response: $response")

        if (response.equals("rs//connected:success")) {
            println("Connected to balancer")
            Thread {
                try {

                    val server = ServerSocket(socket)
                    println("Server started on port $socket")

                    while (true) {
                        val client = server.accept()
                        println("Client connected: ${client.inetAddress.hostAddress}")

                        Thread {
                            handleClient(client, clients, clientLock, servers, serverLock)
                        }.start()
                    }
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                }
            }.start()
        } else {
            println("Failed to connect to balancer")
        }

        response = input.readLine()

        while (response != null) {
            if (response == "exit") {
                break
            }

            if (response.startsWith("cf//new_server")) {
                val splits = response.split(":")
                val server = Server(splits[1], splits[2].toInt())
                synchronized(serverLock) {
                    servers.add(server)
                }
            }
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        balancer.close()
        println("Closing server")
    }
}

fun handleClient(client: Socket, clients: MutableList<Client>, clientLock: Any, servers: MutableList<Server>, serverLock: Any) {
    try {
        val clientInput = BufferedReader(client.getInputStream().reader())
        val clientOutput = PrintWriter(client.getOutputStream(), true)

        var message = clientInput.readLine()

        if (message.startsWith("cf//username")) {
            val splits = message.split(":")
            val username = splits[1]

            val client = Client(username, client)
            synchronized(clientLock) {
                clients.add(client)
            }
            println("Registered client: $username")
        }

        message = clientInput.readLine()

        while (message != null) {
            print("Message: $message")
            if (message == "exit") {
                break
            }

            if (message.startsWith("sm//send_message")) {
                val splits = message.split(":")
                val username = splits[1]
                val message = splits[2]

                var found = false
                synchronized(clientLock) {
                    clients.forEach {
                        if (it.username == username) {
                            it.sendMessage(message)
                            found = true
                        }
                    }
                }
                if (!found) {
                    synchronized(serverLock) {
                        servers.forEach {
                            it.sendMessage("s" + message)
                        }
                    }
                }
            }

            if (message.startsWith("ssm//send_message")) {
                val splits = message.split(":")
                val username = splits[1]
                val message = splits[2]

                synchronized(clientLock) {
                    clients.forEach {
                        if (it.username.equals(username)) {
                            it.sendMessage(message)
                        }
                    }
                }

                client.close()
            }

            if (message.startsWith("rq//available_people")) {
                val splits = message.split(":")
                val server = Server(splits[1], splits[2].toInt())
                synchronized(serverLock) {
                    servers.add(server)
                }
            }

            message = clientInput.readLine()
        }
    } catch (e: Exception) {
        client.close()
        println("Error: ${e.message}")
    } finally {
        client.close()
    }
}