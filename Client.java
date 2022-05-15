import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import javax.swing.JFrame;

public class Client {

	// unit separator
	public static final char SEP = 31;
	public static final char GROUPSEP = 29;

	// client info
	private final int TPS = 60; // client ticks per second
	private boolean running = false;
	private String clientName = null;
	private DatagramSocket clientSocket = null;
	private BufferedReader reader;
	private Player player;
	private byte[] inData;

	// connecting server info
	private int port = -1; // port of server
	private InetAddress serverIP = null; // IP address of server

	// display info
	private static final int WIDTH = 1000, HEIGHT = 500;
	private Display display;

	// other variables
	private static Random rand = new Random();

	public Client() {
		try {
			setup();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void message(String m) {
		System.out.println("System: " + m);
	}

	private void setup() throws IOException {
		clientSocket = new DatagramSocket();
		reader = new BufferedReader(new InputStreamReader(System.in));
		message("Enter your name:");
		System.out.print("> ");
		clientName = reader.readLine().trim();

		message("What is the IP address of the server?");
		System.out.print("> ");
		serverIP = InetAddress.getByName(reader.readLine());

		message("What port are you connecting to?");
		System.out.print("> ");
		port = Integer.parseInt(reader.readLine());

		message("Setup Complete!");

		// initializes player
		player = new Player(WIDTH / 2, HEIGHT / 2);

		// opens up the display
		display = new Display("Client", WIDTH, HEIGHT);
	}

	private void sendData() {
		player.tick(display.getActiveKeys());

		// send data to server
		byte[] outData = new byte[1024];
		int tempPos = edit(outData, clientName, 0);
		player.addInfo(outData, tempPos);
		// message("Sent data to server " + Arrays.toString(outData));
		DatagramPacket sPacket = new DatagramPacket(outData, outData.length, serverIP, port);

		try {
			clientSocket.send(sPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void receiveData() {
		// receive data from server
		inData = new byte[1024];
		DatagramPacket rPacket = new DatagramPacket(inData, inData.length);
		try {
			clientSocket.receive(rPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// message("Received data from server " + Arrays.toString(inData));
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

	private void tick() {
		sendData(); // send data to server
		receiveData(); // receive data from server
	}

	private void render() {
		BufferStrategy bs = display.getCanvas().getBufferStrategy();
		if (bs == null) {
			display.getCanvas().createBufferStrategy(3);
			return;
		}

		Graphics g = bs.getDrawGraphics();
		// clear screen
		g.clearRect(0, 0, WIDTH, HEIGHT);

		display.render(g, inData);

		bs.show();
		g.dispose();
	}

	public void run() {
		running = true;
		while (running) {
			try {
				// tick sends and receives data from server
				tick();
				render();
				Thread.sleep(1000 / TPS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	private class Player implements Comparable<Player> {
		// player width and height in pixels
		public static final int pHalfWidth = 10, pHalfHeight = 25;
		public static final int pWidth = pHalfWidth * 2, pHeight = pHalfHeight * 2;
		public static final int SPEED = 15;

		// other player info
		// x and y represent the center of the player sprite
		int x, y;
		int score = 0; // player score
		String name; // player name
		Color color; // player color

		public Player(int x, int y) {
			this.x = x;
			this.y = y;
			color = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
		}

		// for reading in data
		public Player(String name, int x, int y, Color color, int score) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.color = color;
			this.score = score;
		}

		public void tick(HashSet<Character> activeKeys) {
			if (activeKeys.contains('w') || activeKeys.contains('W'))
				y -= SPEED;
			if (activeKeys.contains('s') || activeKeys.contains('S'))
				y += SPEED;
			if (activeKeys.contains('a') || activeKeys.contains('A'))
				x -= SPEED;
			if (activeKeys.contains('d') || activeKeys.contains('D'))
				x += SPEED;

			// check player bounds
			if (y < pHalfHeight)
				y = pHalfHeight;
			if (x < pHalfWidth)
				x = pHalfWidth;
			if (y > HEIGHT - pHalfHeight)
				y = HEIGHT - pHalfHeight;
			if (x > WIDTH - pHalfWidth)
				x = WIDTH - pHalfWidth;
		}

		// edits byte array to contain player info such as position and color
		// return new index value of pos
		public int addInfo(byte[] data, int pos) {
			pos = edit(data, x, pos);
			pos = edit(data, y, pos);
			pos = edit(data, color.getRed(), pos);
			pos = edit(data, color.getGreen(), pos);
			pos = edit(data, color.getBlue(), pos);
			return pos;
		}

		@Override
		public int compareTo(Player oth) {
			return this.score - oth.score;
		}
	}

	class Display implements KeyListener {

		private JFrame frame;
		private Canvas canvas;

		private String title;
		private int width, height;

		private HashSet<Character> activeKeys;

		public Display(String title, int width, int height) {
			this.title = title;
			this.width = width;
			this.height = height;

			activeKeys = new HashSet<Character>();
			createDisplay();
		}

		private void createDisplay() {
			frame = new JFrame(title);
			frame.setSize(width, height);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setResizable(false);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			canvas = new Canvas();
			canvas.setPreferredSize(new Dimension(width, height));
			canvas.setMinimumSize(new Dimension(width, height));
			canvas.setMaximumSize(new Dimension(width, height));

			frame.addKeyListener(this);
			frame.setFocusable(true);
			canvas.addKeyListener(this);
			canvas.setFocusable(true);

			frame.add(canvas);
			frame.pack();
		}

		// draw a string given x and y coordinates as center
		private void renderCenteredString(Graphics2D g2, String txt, int x, int y) {
			int stringLen = g2.getFontMetrics().stringWidth(txt);
			g2.drawString(txt, x - stringLen / 2, y);
		}

		// shows instructions on the game rules before the game starts
		private void renderHow2Play(Graphics2D g2) {
			g2.setColor(Color.BLACK);
			g2.setFont(new Font("Didot", Font.BOLD, 20));
			renderCenteredString(g2, "1. Avoid the circles", width / 2, height / 2 - 200);
			renderCenteredString(g2, "2. Lowest score wins!", width / 2, height / 2 - 170);
			renderCenteredString(g2, "Your score is indicated by the", width / 2, height / 2 - 140);
			renderCenteredString(g2, "red number underneath your name", width / 2, height / 2 - 110);
		}

		private void renderPlayer(Graphics2D g2, String pName, int x, int y, int score, Color color) {
			int fontSize = 20;

			g2.setColor(color);
			g2.fillRect(x - Player.pHalfWidth, y - Player.pHalfHeight, Player.pWidth, Player.pHeight);
			g2.setColor(Color.black);
			g2.setFont(new Font("Monospaced", Font.BOLD, fontSize));
			renderCenteredString(g2, pName, x, y - Player.pHalfHeight - fontSize);
			g2.setColor(Color.red);
			renderCenteredString(g2, Integer.toString(score), x, y - Player.pHalfHeight - fontSize / 3 + 3);
		}

		private void renderCenteredCircle(Graphics2D g2, int x, int y, int fullRad, int currRad, Color color) {
			int outerX = x - fullRad;
			int outerY = y - fullRad;
			int innerX = x - currRad;
			int innerY = y - currRad;
			g2.setStroke(new BasicStroke(5));
			g2.setColor(color);
			g2.drawOval(outerX, outerY, fullRad * 2, fullRad * 2);
			g2.fillOval(innerX, innerY, currRad * 2, currRad * 2);
		}

		private void renderLeaderboard(Graphics2D g2, Player[] players) {
			Arrays.sort(players);

			g2.setColor(Color.BLACK);
			g2.setFont(new Font("Didot", Font.BOLD, 60));
			renderCenteredString(g2, "Final Standings (^-^)", width / 2, 70);
			int leaderboardPosition = 1, currY = 140;
			g2.setColor(Color.black);
			for (int i = 0; i < players.length; i++) {
				if (i > 0 && players[i - 1].score < players[i].score)
					leaderboardPosition++;
				int spaceIncrement = 50;
				if (leaderboardPosition == 1) {
					g2.setFont(new Font("Didot", Font.ITALIC | Font.BOLD, 50));
					g2.setColor(new Color(252, 194, 1));
				} else if (leaderboardPosition == 2) {
					g2.setFont(new Font("Didot", Font.BOLD, 40));
					g2.setColor(new Color(167, 167, 173));
					spaceIncrement = 40;
				} else if (leaderboardPosition == 3) {
					g2.setFont(new Font("Didot", Font.BOLD, 30));
					g2.setColor(new Color(167, 112, 68));
					spaceIncrement = 30;
				} else {
					g2.setFont(new Font("Didot", Font.BOLD, 25));
					g2.setColor(Color.black);
					spaceIncrement = 25;
				}
				renderCenteredString(g2, "#" + leaderboardPosition + " " + players[i].name, width / 2, currY);
				currY += spaceIncrement;
			}
		}

		public void render(Graphics g, byte[] inData) {
			Graphics2D g2 = (Graphics2D) g;
			String receivedData = new String(inData);
			String[] groups = receivedData.split(GROUPSEP + "");
			String[] data1 = groups[0].split(SEP + ""); // player data
			String[] data2 = groups[1].split(SEP + ""); // circle data
			String[] data3 = groups[2].split(SEP + ""); // initial count-down clock data
			String[] data4 = groups[3].split(SEP + ""); // game timer clock data

			// player data
			int numPacketsPerPlayer = 7; // number of packets sent per player
			Player[] players = new Player[data1.length / numPacketsPerPlayer];

			// read in player data
			for (int i = 0; i < players.length; i ++) {
				int idx = i*numPacketsPerPlayer;
				int pX = Integer.parseInt(data1[idx + 1]);
				int pY = Integer.parseInt(data1[idx + 2]);
				int R = Integer.parseInt(data1[idx + 3]);
				int G = Integer.parseInt(data1[idx + 4]);
				int B = Integer.parseInt(data1[idx + 5]);
				int score = Integer.parseInt(data1[idx + 6]);
				players[i] = new Player(data1[idx], pX, pY, new Color(R, G, B), score);
			}

			// render circles
			for (int i = 0; i < data2.length - 6; i += 7) {
				int cX = Integer.parseInt(data2[i]);
				int cY = Integer.parseInt(data2[i + 1]);
				int fullRadius = Integer.parseInt(data2[i + 2]);
				int currRadius = Integer.parseInt(data2[i + 3]);
				int R = Integer.parseInt(data2[i + 4]);
				int G = Integer.parseInt(data2[i + 5]);
				int B = Integer.parseInt(data2[i + 6]);
				renderCenteredCircle(g2, cX, cY, fullRadius, currRadius, new Color(R, G, B));
			}

			// render stuff before game starts
			if (data3[0].length() > 0) {
				renderHow2Play(g2);
				g2.setColor(Color.RED);
				g2.setFont(new Font("Didot", Font.BOLD, 100));
				renderCenteredString(g2, data3[0], width / 2, height / 2);
			} else { // render timer clock
				if (data4[0].length() > 0) { // timer is still going
					g2.setColor(Color.BLUE);
					g2.setFont(new Font("Didot", Font.BOLD, 100));
					renderCenteredString(g2, data4[0], width / 2, height / 2);
				} else { // game is over
					renderLeaderboard(g2, players);
					// running = false;
					// return;
				}
			}

			// render players
			for (Player p : players)
				renderPlayer(g2, p.name, p.x, p.y, p.score, p.color);
		}

		public Canvas getCanvas() {
			return canvas;
		}

		public JFrame getFrame() {
			return frame;
		}

		public HashSet<Character> getActiveKeys() {
			return activeKeys;
		}

		@Override
		public void keyTyped(KeyEvent e) {

		}

		@Override
		public void keyPressed(KeyEvent e) {
			activeKeys.add(e.getKeyChar());
		}

		@Override
		public void keyReleased(KeyEvent e) {
			activeKeys.remove(e.getKeyChar());
		}

	}

	public static void main(String[] args) {
		Client client = new Client();
		client.run();
	}

}