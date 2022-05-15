import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class Server {

	// unit separator
	public static final char SEP = 31;
	public static final char GROUPSEP = 29;

	// server info
	private final int TPS = 100; // server ticks per second
	private boolean running;
	private int port = -1;
	private DatagramSocket serverSocket = null;

	// data to sent back to client(s)
	private DatagramPacket serverPacket;

	// display info
	private static final int WIDTH = 1000, HEIGHT = 500;

	// game data
	private HashMap<InetAddress, ClientPlayer> clientData;
	private ArrayList<Circle> circles;
	private long timer = 15 * TPS; // timer starts counting down when game starts
	private boolean countDown = true; // initial count-down clock before game starts
	private int waitTime = 10 * TPS; // ticks to wait before spawning the next circle

	// other variables
	private static Random rand = new Random();

	public Server() {
		setup();
	}

	private void message(String m) {
		System.out.println("System: " + m);
	}

	private void setup() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		message("What port would you like to host this server on?");
		System.out.print("> ");
		try {
			port = Integer.parseInt(br.readLine());
			br.close();
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}
		try {
			serverSocket = new DatagramSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}

		message("Setup Complete!");

		// sets up player data Hash-Map
		clientData = new HashMap<InetAddress, ClientPlayer>();

		// sets up ArrayList of circles
		circles = new ArrayList<Circle>();
	}

	// edit data[] byte array to include a string value starting at pos
	// return the new index value of pos
	private int edit(byte[] data, String str, int pos) {
		for (int i = 0; i < str.length(); i++)
			data[pos++] = (byte) (str.charAt(i));
		data[pos++] = SEP;
		return pos;
	}

	private int edit(byte[] data, int value, int pos) {
		return edit(data, Integer.toString(value), pos);
	}

	// create a byte array to send player data back to client
	private int addPlayerData(byte[] data, int pos) {
		for (InetAddress clientAddress : clientData.keySet()) {
			ClientPlayer client = clientData.get(clientAddress);
			pos = edit(data, client.name, pos);
			pos = edit(data, client.x, pos);
			pos = edit(data, client.y, pos);
			pos = edit(data, client.R, pos);
			pos = edit(data, client.G, pos);
			pos = edit(data, client.B, pos);
			pos = edit(data, client.score, pos);
		}
		data[pos++] = GROUPSEP;
		// message("Return data to client " + Arrays.toString(ret));
		return pos;
	}

	// create a byte array to send circle data back to client
	private int addCircleData(byte[] data, int pos) {
		for (Circle c : circles)
			pos = c.addInfo(data, pos);
		data[pos++] = GROUPSEP;
		// message("Return data to client " + Arrays.toString(ret));
		return pos;
	}

	// receive data from client
	private void receiveData(DatagramPacket clientPacket, InetAddress clientAddress) {
		String receivedData = new String(clientPacket.getData());
		String[] data = receivedData.split(SEP + "");
		// received data from client
		if (!clientData.containsKey(clientAddress))
			clientData.put(clientAddress, new ClientPlayer(data[0]));
		ClientPlayer client = clientData.get(clientAddress);
		client.x = data[1];
		client.y = data[2];
		client.R = data[3];
		client.G = data[4];
		client.B = data[5];
	}

	// send data back to client
	private void sendData(InetAddress clientAddress, int clientPort) {
		// send data back to client
		// send player and circle information
		byte[] outData = new byte[1024];
		int pos = addPlayerData(outData, 0); // player data
		pos = addCircleData(outData, pos); // circle data
		if (countDown) { // initial count-down clock data
			String waitTimeSecs = String.format("%.2f", (waitTime + 0.0) / TPS);
			pos = edit(outData, waitTimeSecs, pos);
		}
		outData[pos++] = GROUPSEP;
		// game timer data
		if (!countDown && timer > 0) {
			String timerSecs = String.format("%.2f", (timer + 0.0) / TPS);
			pos = edit(outData, timerSecs, pos);
		}
		outData[pos++] = GROUPSEP;
		// create packet with all this data
		serverPacket = new DatagramPacket(outData, outData.length, clientAddress, clientPort);
		try {
			serverSocket.send(serverPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// message("Data sent from Server to client " + clientAddress.getHostAddress());
	}

	private void tick() {
		// take in client data
		// server waiting for client request
		byte[] inData = new byte[512];
		DatagramPacket clientPacket = new DatagramPacket(inData, inData.length);
		try {
			serverSocket.receive(clientPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// if game has started don't let anyone else join
		// although, they can still spectate the game :)
		InetAddress clientAddress = clientPacket.getAddress();
		if (countDown || clientData.containsKey(clientAddress))
			receiveData(clientPacket, clientAddress);

		// manage circles
		// deactivate completed circles
		for (int i = circles.size() - 1; i >= 0; i--) {
			Circle c = circles.get(i);
			if (c.isDone()) {
				circles.remove(i);
				if (timer > 0) { // only add score if game is still going on
					for (InetAddress ip : clientData.keySet()) {
						ClientPlayer p = clientData.get(ip);
						int pX = Integer.parseInt(p.x);
						int pY = Integer.parseInt(p.y);
						// check if player (x, y) is in the circle
						int dist = (pX - c.x) * (pX - c.x) + (pY - c.y) * (pY - c.y);
						if (dist < c.fullRadius * c.fullRadius)
							p.score += c.addScore[c.difficulty];
					}
				}
			} else
				circles.get(i).tick();
		}

		// create a new circle
		waitTime--;
		if (waitTime <= 0) {
			countDown = false;
			// spawn circle
			circles.add(new Circle(rand.nextInt(Circle.MAX_DIFFICULTY), WIDTH, HEIGHT));
			// reset wait time
			waitTime = rand.nextInt(5, TPS / 3);
		}

		// send out data
		sendData(clientPacket.getAddress(), clientPacket.getPort());
		if (!countDown && timer > 0)
			timer--;
	}

	public void run() {
		running = true;
		try {
			message("Server is running at the address '" + InetAddress.getLocalHost() + "' on port '" + port + "'");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		while (running) {
			tick();
			try {
				Thread.sleep(1000 / TPS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	// represents a specific Client
	private class ClientPlayer {
		String name, x, y, R, G, B;
		int score = 0;

		public ClientPlayer(String name) {
			this.name = name;
		}
	}

	private class Circle {

		// values for each difficulty
		final int[] minRadii = { 50, 100, 150, 200, 250 };
		final int[] maxRadii = { 100, 200, 300, 400, 500 };
		// deploySpeed is number of pixel increase in radius size every tick
		final int[] deploySpeed = { 1, 2, 4, 8, 16 };
		// score to add to player for each circle difficulty
		final int[] addScore = { 25, 16, 9, 4, 1 };
		final Color[] colors = { new Color(248, 131, 121), new Color(236, 88, 0), new Color(255, 49, 49),
				new Color(196, 30, 58), new Color(112, 41, 99) };
		public static final int MAX_DIFFICULTY = 5;

		// full radius is the maximum radius this circle will grow to
		int fullRadius;
		// current radius is the current radius of the circle
		int currentRadius;
		// difficulty of circle to avoid affects its grow speed and full radius
		// difficulty rating goes from 0 to MAX_DIFFICULTY (0 being the easiest)
		int difficulty;
		int x, y;

		public Circle(int difficulty, int screenWidth, int screenHeight) {
			this.difficulty = difficulty;
			if (this.difficulty > MAX_DIFFICULTY)
				this.difficulty = MAX_DIFFICULTY;

			// initialize variables
			currentRadius = 1;
			fullRadius = rand.nextInt(minRadii[difficulty], maxRadii[difficulty]);
			x = rand.nextInt(screenWidth);
			y = rand.nextInt(screenHeight);
		}

		public void tick() {
			currentRadius += deploySpeed[difficulty];
		}

		// check if this circle is done growing
		public boolean isDone() {
			return currentRadius >= fullRadius;
		}

		// edits byte array to contain circle info such as position, radius, and color
		// return new index value of pos
		public int addInfo(byte[] data, int pos) {
			pos = edit(data, x, pos);
			pos = edit(data, y, pos);
			pos = edit(data, fullRadius, pos);
			pos = edit(data, currentRadius, pos);
			pos = edit(data, colors[difficulty].getRed(), pos);
			pos = edit(data, colors[difficulty].getGreen(), pos);
			pos = edit(data, colors[difficulty].getBlue(), pos);
			return pos;
		}
	}

	public static void main(String[] args) {
		Server server = new Server();
		server.run();
	}

}
