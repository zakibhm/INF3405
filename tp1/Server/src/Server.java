
import java.net.*;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.awt.image.BufferedImage;

public class Server {

	private static HashMap<String, String> usersDatabase = new HashMap<>();
	private static ServerSocket listener;
	private static final String IP_ADDRESS_PATTERN = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
	private static final Pattern pattern = Pattern.compile(IP_ADDRESS_PATTERN);
	private static int serverPort = 0;
	private static String serverAddress = null;

	public static boolean validateUser(String username, String password) {

		// Vérifie si l'utilisateur existe et si le mot de passe correspond

		String storedPassword = usersDatabase.get(username);
		return storedPassword != null && storedPassword.equals(password);
	}

	public static boolean validateIPAddress(String serverAddress) {

		Matcher matcher = pattern.matcher(serverAddress);
		return matcher.matches();
	}

	public static boolean validatePort(int serverPort) {
		if (serverPort >= 5000 && serverPort <= 5050)
			return true;
		return false;
	}

	private static void loadDatabaseFromCSV() {

		String dataBasePath = Paths.get("user_database.csv").toAbsolutePath().toString();
		try (BufferedReader reader = new BufferedReader(new FileReader(dataBasePath))) {
			System.out.println("Loading database from CSV: " + dataBasePath);
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(",");
				if (parts.length == 2) {
					usersDatabase.put(parts[0], parts[1]);
				}
			}
			System.out.println("Database loaded from CSV.");
		} catch (IOException e) {
			System.err.println("Error loading database from CSV: " + e.getMessage());
		}
	}

	private static void creatingServer() {

		boolean isValid = false;
		Scanner scanner = new Scanner(System.in);
		String serverAddress = null;
		int serverPort = 0;

		while (!isValid) {

			System.out.print("Veuillez entrer l'addresse IP du poste sur lequel s'éxecute le serveur  : \n");
			serverAddress = scanner.nextLine();
			if (validateIPAddress(serverAddress)) {
				isValid = true;
			} else {
				System.out.print("Addresse IP est invalide! \n");

			}

		}
		isValid = false;
		while (!isValid) {
			System.out.print("Veuillez entrer le numero de port d’écoute: \n");
			serverPort = scanner.nextInt();
			if (validatePort(serverPort)) {
				isValid = true;
			} else {
				System.out.print("Numero de port est invalide! \n");
			}

		}
		scanner.close();
		try {
			listener = new ServerSocket();
			listener.setReuseAddress(true);
			InetAddress serverIP = InetAddress.getByName(serverAddress);

			listener.bind(new InetSocketAddress(serverIP, serverPort));
			System.out.format("the server is running %s:%d%n", serverAddress, serverPort);
		} catch (IOException e) {
			System.out.println("Could not create server socket");
			e.printStackTrace();
			return;
		}
	}

	public static void main(String[] args) throws Exception {
		int clientNumber = 0;

		// creating the server
		creatingServer();

		// loading the dataBase
		loadDatabaseFromCSV();

		// waiting for clients
		try {
			while (true) {
				new ClientHandler(listener.accept(), clientNumber++).start();
			}
		} finally {
			listener.close();
		}
	}

	public static class ClientHandler extends Thread {
		private Socket socket;
		private int clientNumber;
		private DataInputStream in;
		private DataOutputStream out;

		public ClientHandler(Socket socket, int clientNumber) {
			this.socket = socket;
			this.clientNumber = clientNumber;
			System.out.println("New connection with client#" + clientNumber + " at " + socket);
		}

		public static void saveDatabaseToCSV() {
			try (PrintWriter writer = new PrintWriter(new FileWriter("user_database.csv"))) {
				for (Map.Entry<String, String> entry : usersDatabase.entrySet()) {
					writer.println(entry.getKey() + "," + entry.getValue());
				}
				System.out.println("Database saved to CSV.");
			} catch (IOException e) {
				System.err.println("Error saving database to CSV: " + e.getMessage());
			}
		}

		public String[] getUserinfo() {

			String[] result = null;

			try {
				in = new DataInputStream(socket.getInputStream());
				result = new String[2];
				result[0] = in.readUTF();
				result[1] = in.readUTF();
			} catch (IOException e) {
				System.out.println("Could not read an answer from the client");
				e.printStackTrace();
				return null;
			}
			return result;
		}

		public void searchUser(String username, String password) {

			String messagetoClient = null;

			// verify if the user is already in the database
			if (!usersDatabase.containsKey(username)) {

				// Creating new user
				usersDatabase.put(username, password);
				messagetoClient = "NEW";
				// saving user iformations in the CSV file
				saveDatabaseToCSV();
			} else {
				// verify the password
				String storedPassword = usersDatabase.get(username);
				if (storedPassword.equals(password)) {
					messagetoClient = "SUCCESS";
				} else {
					messagetoClient = "FAILED";
				}

			}
			try {
				out = new DataOutputStream(socket.getOutputStream());
				out.writeUTF(messagetoClient);
			} catch (IOException e) {
				System.out.println("Could not send confirmation to client");
			}
		}

		// this function is used to print the image and user informations received from
		// the client
		private void terminalReceivedMessage(String username, String imageName) {
			System.out
					.println("[" + username + " - " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " - "
							+ new SimpleDateFormat("yyyy-MM-dd@HH:mm:ss").format(new Date()) + "]" + " : Image " + imageName
							+ " received for processing");

		}

		// extract the image name and the image data from the client
		private Object[] readImage(String username) {
			// Read the image data sent by the client
			Object[] result = new Object[2];
			try {
				String imageName = in.readUTF();
				result[0] = imageName;
				terminalReceivedMessage(username, imageName);

				int imageDataLength = in.readInt();
				byte[] imageData = new byte[imageDataLength];
				in.readFully(imageData);
				result[1] = imageData;
			} catch (IOException e) {
				System.out.println("Could not read the image data sent by the client");
				e.printStackTrace();
			}
			return result;
		}

		// this function is used to send the sobel image to the client
		private void sendSobelImage(String imageName, byte[] imageData) {
			try {
				// Convert byte array to BufferedImage
				BufferedImage bufferedImage = byteArrayToBufferedImage(imageData);

				BufferedImage sobelImage = Sobel.process(bufferedImage);
				String sobelImageName = "sobel_" + imageName;
				out.writeUTF(sobelImageName);
				// Convert Sobel image to byte array
				byte[] sobelImageData = bufferedImageToByteArray(sobelImage);
				int sobelImageDataLength = sobelImageData.length;

				// Send the length of the Sobel image data first
				out.writeInt(sobelImageDataLength);

				// Send the Sobel image data
				out.write(sobelImageData);

			} catch (IOException e) {
				System.out.println("Could not send the image to  client");
				e.printStackTrace();
				return;
			}

		}

		// this function is used to hanlde all image operations between the server and
		// the client
		private void imageHandler(String username) {
			Object[] imageData = readImage(username);

			// Send the Sobel image to the client
			sendSobelImage((String) imageData[0], (byte[]) imageData[1]);
		}

		private byte[] bufferedImageToByteArray(BufferedImage image) {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(image, "jpg", baos);
				return baos.toByteArray();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}

		private BufferedImage byteArrayToBufferedImage(byte[] imageData) {
			try {
				ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
				return ImageIO.read(bis);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}

		public void run() {

			String[] userInfo = getUserinfo();
			searchUser(userInfo[0], userInfo[1]);

			imageHandler(userInfo[0]);

			try {
				socket.close();
			} catch (IOException e) {
				System.out.println("Could not close socket");
			}

			System.out.println("Connection with client#" + clientNumber + " closed");
		}

	}
}
