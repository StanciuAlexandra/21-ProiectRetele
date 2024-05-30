import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int SERVER_PORT = 55555;
    private static Map<InetSocketAddress, Integer> activeClients = new HashMap<>();
    private static Queue<Task> taskQueue = new LinkedList<>();
    private static Set<String> completedTaskIds = new HashSet<>();

    private static Map<String, InetSocketAddress> clientRequests = new HashMap<>();

    public static void main(String[] args) {
        try {
            DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT, InetAddress.getByName("localhost"));
            byte[] receiveData = new byte[1024];

            System.out.println("Serverul este pornit...");
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                System.out.println("Mesaj primit de la client (" + clientAddress.getHostAddress() + ":" + clientPort + "): " + message);

                if (message.startsWith("REGISTER:")) {
                    registerClient(clientAddress, clientPort);
                } else if (message.startsWith("UNREGISTER:")) {
                    unregisterClient(clientAddress, clientPort);
                } else if (message.startsWith("PROCESS:")) {
                    processRequest(message.substring(8), clientAddress, clientPort);
                } else if (message.startsWith("TASK_COMPLETE:")) {
                    handleTaskCompletion(message.substring(14), clientAddress, clientPort);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void registerClient(InetAddress clientAddress, int clientPort) throws IOException {
        activeClients.put(new InetSocketAddress(clientAddress, clientPort), clientPort);
        System.out.println("Client inregistrat: " + clientAddress.getHostAddress() + ":" + clientPort);
        String response = "REGISTERED:" + clientPort;
        sendDataToClient(response.getBytes(), clientAddress, clientPort);
        System.out.println("Raspuns trimis catre client (" + clientAddress.getHostAddress() + ":" + clientPort + "): " + response);
    }

    private static synchronized void unregisterClient(InetAddress clientAddress, int clientPort) throws IOException {
        activeClients.remove(new InetSocketAddress(clientAddress, clientPort));
        System.out.println("Client sters: " + clientAddress.getHostAddress() + ":" + clientPort);
    }

    private static synchronized void processRequest(String request, InetAddress clientAddress, int clientPort) throws IOException {
        String[] parts = request.split(" ");
        if (parts.length < 1) {
            System.err.println("Cerere de procesare invalida.");
            return;
        }

        String command = parts[0];
        String[] arguments = Arrays.copyOfRange(parts, 1, parts.length);

        System.out.println("Cerere de procesare primita de la client (" + clientAddress.getHostAddress() + ":" + clientPort + "): " + command);

        Task task = new Task(UUID.randomUUID().toString(), command, Arrays.asList(arguments));
        taskQueue.add(task);

        clientRequests.put(task.getId(), new InetSocketAddress(clientAddress, clientPort));
        System.out.println("Adaugat task in clientRequests cu ID-ul: " + task.getId());

        sendNextTaskToProcessingClient();
    }

    private static synchronized void sendNextTaskToProcessingClient() throws IOException {
        if (!activeClients.isEmpty() && !taskQueue.isEmpty()) {
            InetSocketAddress processingClient = activeClients.keySet().iterator().next();
            Integer clientPort = activeClients.get(processingClient);

            Task task = taskQueue.poll();
            if (task != null && !completedTaskIds.contains(task.getId())) {
                System.out.println("Trimitere task catre client (" + processingClient.getAddress().getHostAddress() + ":" + clientPort + "): " + task.getCommand());

                sendTaskToClient(task, processingClient.getAddress(), clientPort);
            }
        }
    }

    private static synchronized void sendTaskToClient(Task task, InetAddress clientAddress, int clientPort) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(task);
        byte[] sendData = outputStream.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
        socket.send(sendPacket);
        objectOutputStream.close();
        outputStream.close();
        socket.close();
    }

    private static synchronized void handleTaskCompletion(String taskCompleteMessage, InetAddress clientAddress, int clientPort) {
        String[] parts = taskCompleteMessage.split(":", 2);
        if (parts.length != 2) {
            System.err.println("Mesaj de completare a task-ului invalid: " + taskCompleteMessage);
            return;
        }

        String taskId = parts[0];
        String taskInfo = parts[1];

        String[] taskInfoParts = taskInfo.split(";", 4);
        if (taskInfoParts.length != 4) {
            System.err.println("Mesaj de task complet invalid: " + taskCompleteMessage);
            return;
        }

        if (completedTaskIds.contains(taskId)) {
            return;
        }

        InetSocketAddress requesterAddress = clientRequests.get(taskId);
        if (requesterAddress == null) {
            System.err.println("Nu s-a putut găsi clientul care a trimis cererea pentru taskId-ul: " + taskId);
            return;
        }

        int exitCode = Integer.parseInt(taskInfoParts[2]);
        String result = taskInfoParts[3];

        try {
            List<String> resultList = new ArrayList<>();
            resultList.add("Exit code: " + exitCode);
            resultList.add("Result: " + result);

            Task responseTask = new Task(taskId, "Rezultat", resultList);
            completedTaskIds.add(taskId);
            sendTaskToClient(responseTask, requesterAddress.getAddress(), requesterAddress.getPort());
            System.out.println("Rezultatul task-ului trimis catre client (" + requesterAddress.getAddress().getHostAddress() + ":" + requesterAddress.getPort() + "): " + resultList);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            sendNextTaskToProcessingClient();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendDataToClient(byte[] data, InetAddress host, int port) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = new DatagramPacket(data, data.length, host, port);
        socket.send(packet);
        socket.close();
    }
}
