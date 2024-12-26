import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;

public class Server {
    private static final int PORT = 80;
    private static Map<String, ClientHandler> clients = new HashMap<>();
    private static final String FILES_DIR = "./pics";

    public static void main(String[] args) {
        new File(FILES_DIR).mkdirs();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("Ошибка сервера: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private String clientName;
        private BufferedReader in;
        private PrintWriter out;
        private DataOutputStream dataOut;
        private DataInputStream dataIn;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                dataOut = new DataOutputStream(socket.getOutputStream());
                dataIn = new DataInputStream(socket.getInputStream());

                out.println("Введите @имя номер_файла чтобы отправить пользователю картинку");
                clientName = in.readLine();
                synchronized (clients) {
                    clients.put(clientName, this);
                }
                System.out.println(clientName + " подключился");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equals("/files")) {
                        sendFilesList();
                    } else if (message.startsWith("@")) {
                        String[] parts = message.split(" ", 2);
                        if (parts.length == 2) {
                            String recipientName = parts[0].substring(1);
                            String fileName = parts[1];

                            sendImageToClient(clientName, recipientName, fileName);
                        } else {
                            out.println("Неверный формат сообщения. Используйте: @имя номер_файла");
                        }
                    } else if (message.equals("/users")) {
                        sendClientList();
                    } else {
                        out.println("Сообщения должны начинаться с @имя или быть командой /users");
                    }
                }
            } catch (IOException e) {
                System.out.println("Ошибка клиента: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void sendFilesList() {
            File folder = new File(FILES_DIR);
            File[] files = folder.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".jpg") ||
                            name.toLowerCase().endsWith(".png") ||
                            name.toLowerCase().endsWith(".gif"));

            out.println("Список доступных файлов:");
            if (files != null && files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    out.println((i + 1) + ". " + files[i].getName());
                }
            } else {
                out.println("Нет доступных файлов.");
            }
        }

        private void sendClientList() {
            synchronized (clients) {
                out.println("Клиенты в чате:");
                for (String name : clients.keySet()) {
                    if (!name.equals(clientName)) {
                        out.println("- " + name);
                    }
                }
            }
        }

        private void sendImageToClient(String sender, String recipientName, String fileIndex) {
            try {
                File folder = new File(FILES_DIR);
                File[] files = folder.listFiles();

                if (files == null || files.length == 0) {
                    out.println("Нет доступных файлов.");
                    return;
                }

                int index = Integer.parseInt(fileIndex) - 1;
                if (index < 0 || index >= files.length) {
                    out.println("Неверный номер файла.");
                    return;
                }

                File fileToSend = files[index];
                byte[] fileData = Files.readAllBytes(fileToSend.toPath());

                ClientHandler recipient;
                synchronized (clients) {
                    recipient = clients.get(recipientName);
                }

                if (recipient != null) {
                    recipient.out.println("INCOMING_IMAGE " + fileToSend.getName() + " " + sender);
                    recipient.out.flush();

                    Thread.sleep(100);

                    recipient.dataOut.writeInt(fileData.length);
                    recipient.dataOut.write(fileData);
                    recipient.dataOut.flush();

                    out.println("Файл успешно отправлен пользователю " + recipientName);
                } else {
                    out.println("Пользователь " + recipientName + " не найден.");
                }
            } catch (IOException | InterruptedException e) {
                out.println("Ошибка при отправке файла: " + e.getMessage());
            } catch (NumberFormatException e) {
                out.println("Пожалуйста, введите корректный номер файла.");
            }
        }

        private void disconnect() {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Ошибка закрытия сокета: " + e.getMessage());
            }
            synchronized (clients) {
                clients.remove(clientName);
            }
            System.out.println(clientName + " отключился.");
        }
    }
}