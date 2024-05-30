import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Objects;

public class Client {
    private static final int SERVER_PORT = 55555;

    public static void main(String[] args) {
        try {
            DatagramSocket clientSocket = new DatagramSocket();

            String registerMessage = "REGISTER:";
            byte[] sendData = registerMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("localhost"), SERVER_PORT);
            clientSocket.send(sendPacket);
            System.out.println("Mesaj trimis catre server: " + registerMessage);

            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            System.out.println("Mesaj primit de la server: " + response);

            if (response.startsWith("REGISTERED:")) {
                int clientPort = Integer.parseInt(response.split(":")[1]);
                System.out.println("Clientul a fost inregistrat cu succes. Port dinamic: " + clientPort);

                new Thread(() -> {
                    try {
                        while (true) {
                            byte[] taskData = new byte[1024];
                            DatagramPacket taskPacket = new DatagramPacket(taskData, taskData.length);
                            clientSocket.receive(taskPacket);

                            ByteArrayInputStream inputStream = new ByteArrayInputStream(taskPacket.getData());
                            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                            Task task = (Task) objectInputStream.readObject();

                            if(Objects.equals(task.getCommand(),"Rezultat")){
                                System.out.println("Rezultatul primit de la server: " + task.getResult());
                            }
                            else {
                                System.out.println("Task primit de la server: " + task.getCommand());
                                System.out.println("Argumente primite de la server: " + task.getResult());
                            }
                                List<String> resultList = task.getResult();
                                String argumente = String.join(" ", resultList);
                                int exitCode = executeCommand(task.getCommand()+" "+argumente);

                                String taskCompleteMessage = "TASK_COMPLETE:" + task.getId() + ":localhost;" + clientPort + ";" + exitCode + ";" + task.getResult();
                                byte[] sendDataComplete = taskCompleteMessage.getBytes();
                                DatagramPacket sendCompletePacket = new DatagramPacket(sendDataComplete, sendDataComplete.length, InetAddress.getByName("localhost"), SERVER_PORT);
                                clientSocket.send(sendCompletePacket);

                                objectInputStream.close();
                                inputStream.close();
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }).start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                while (true) {
                    System.out.print("Introduceți comanda: ");
                    String command = reader.readLine();

                    if (command.equalsIgnoreCase("exit")) {
                        String unregisterMessage = "UNREGISTER:localhost:" + clientSocket.getLocalPort();
                        byte[] unregisterSendData = unregisterMessage.getBytes();
                        DatagramPacket unregisterSendPacket = new DatagramPacket(unregisterSendData, unregisterSendData.length, InetAddress.getByName("localhost"), SERVER_PORT);
                        clientSocket.send(unregisterSendPacket);
                        System.out.println("Mesaj trimis catre server: " + unregisterMessage);

                        break;
                    }

                    String processRequest = "PROCESS:" + command;
                    byte[] processSendData = processRequest.getBytes();
                    DatagramPacket processSendPacket = new DatagramPacket(processSendData, processSendData.length, InetAddress.getByName("localhost"), SERVER_PORT);
                    clientSocket.send(processSendPacket);
                    System.out.println("Mesaj trimis catre server: " + processRequest);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int executeCommand(String command) {
        if(command.startsWith("Rezultat")){
            return 0;
        }else {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                String[] cmd;

                if (os.contains("win")) {
                    cmd = new String[]{"cmd", "/c", command};
                } else {
                    cmd = new String[]{"/bin/sh", "-c", command};
                }
                ProcessBuilder builder = new ProcessBuilder(cmd);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                int exitCode = process.waitFor();
                return exitCode;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return -1;
            }
        }
    }

}
