import java.io.PrintWriter
import java.net.Socket

class Server(val ip: String, val socket: Int) {
    fun sendMessage(message: String) {
        val client = Socket(ip, socket)
        val clientOutput = PrintWriter(client.getOutputStream(), true)

        clientOutput.println(message)

        client.close()
    }
}