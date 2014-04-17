package me.libraryaddict.uuid;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;

import com.mojang.api.profiles.HttpProfileRepository;
import com.mojang.api.profiles.Profile;

public class ConvertUUIDs {

	private static Connection con;

	public static Boolean withDashes;

	private static String temp, mysql_Database, mysql_IP, mysql_Password,
			mysql_Player_Column, mysql_Table, mysql_User, mysql_UUID_Column;

	private static ArrayList<Long> perSecond = new ArrayList<Long>();

	private static final HttpProfileRepository profileRepository = new HttpProfileRepository(
			"minecraft");

	private static Connection connectMysql() {
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			String conn = "jdbc:mysql://" + mysql_IP + "/" + mysql_Database;
			return DriverManager
					.getConnection(conn, mysql_User, mysql_Password);
		} catch (ClassNotFoundException ex) {
			System.err.println("[ConvertUUIDs] No MySQL driver found!");
		} catch (SQLException ex) {
			System.err
					.println("[ConvertUUIDs] Error while fetching MySQL connection!");
		} catch (Exception ex) {
			System.err
					.println("[ConvertUUIDs] Unknown error while fetching MySQL connection.");
		}
		return null;
	}

	private static Connection getConnection() throws SQLException {
		if (con == null || con.isClosed()) {
			con = connectMysql();
		}
		try {
			con.createStatement().execute("DO 1");
		} catch (Exception ex) {
			con = connectMysql();
		}
		return con;
	}

	public static String getUUID(String name) {
		Profile[] profiles = profileRepository.findProfilesByNames(name);
		if (profiles.length > 0) {
			return profiles[0].getId();
		} else {
			return null;
		}
	}

	public static String uuidWithDashes(String s) {
		String fin = "";
		int count = 0;

		for (char ch : s.toCharArray()) {
			count++;

			if (count != 8 && count != 12 && count != 16 && count != 20) {
				fin += ch;
			} else {
				fin += ch + "-";
			}
		}
		return fin;
	}

	private static String getSeconds() {
		if (!perSecond.isEmpty()) {
			perSecond.set(perSecond.size() - 1, System.currentTimeMillis()
					- perSecond.get(perSecond.size() - 1));
		}
		double total = 0;
		for (long l : perSecond) {
			total += (double) l / 1000;
		}
		perSecond.add(System.currentTimeMillis());
		if (perSecond.size() > 10) {
			perSecond.remove(0);
		}
		total /= perSecond.size();
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumFractionDigits(2);
		nf.setMaximumFractionDigits(2);
		return nf.format(total);
	}

	public static void main(String[] args) {

		Scanner scan = new Scanner(System.in);

		do {
			System.out.println("Use dashes in UUIDs? (y/n) ");
			temp = scan.nextLine();
		} while (!(temp.equalsIgnoreCase("y") || temp.equalsIgnoreCase("n")));

		if (temp.equalsIgnoreCase("y")) {
			withDashes = true;
		} else if (temp.equalsIgnoreCase("n")) {
			withDashes = false;
		}

		System.out.println("Database name: ");
		mysql_Database = scan.nextLine();

		System.out.println("Database IP: ");
		mysql_IP = scan.nextLine();

		System.out.println("Database username: ");
		mysql_User = scan.nextLine();

		System.out.println("Database password: ");
		mysql_Password = scan.nextLine();

		System.out.println("Database table: ");
		mysql_Table = scan.nextLine();

		System.out.println("Name column: (i.e. names) ");
		mysql_Player_Column = scan.nextLine();

		System.out.println("UUID column (i.e. uuids): ");
		mysql_UUID_Column = scan.nextLine();

		scan.close();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					if (con != null) {
						con.close();
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});

		try {
			PreparedStatement stmt = getConnection().prepareStatement(
					"SELECT " + mysql_Player_Column + " FROM " + mysql_Table
							+ " WHERE " + mysql_UUID_Column + "=''");
			ResultSet r = stmt.executeQuery();
			r.beforeFirst();
			HashSet<String> uuids = new HashSet<String>();
			while (r.next()) {
				uuids.add(r.getString("name"));
			}
			System.out.println("Found " + uuids.size() + " names to do");
			stmt.close();
			double uuidsCompleted = 0;
			int failedNames = 0;
			stmt = getConnection().prepareStatement(
					"UPDATE " + mysql_Table + " SET " + mysql_UUID_Column
							+ "=? WHERE `" + mysql_Player_Column + "` = ?");
			Iterator<String> itel = uuids.iterator();
			while (itel.hasNext()) {
				String name = itel.next();
				String uuid = getUUID(name);

				if (withDashes) {
					uuid = uuidWithDashes(uuid);
				}

				if (uuid == null) {
					System.out.println("Cannot fetch UUID for player " + name
							+ ". Skipping him!");
					if (!perSecond.isEmpty()) {
						perSecond.remove(perSecond.size() - 1);
						perSecond.add(System.currentTimeMillis());
					}
					failedNames++;
					itel.remove();
					continue;
				}
				stmt.setString(1, uuid);
				stmt.setString(2, name);
				stmt.execute();
				System.out
						.println("Status: "
								+ (int) uuidsCompleted++
								+ "/"
								+ uuids.size()
								+ " || "
								+ (int) ((uuidsCompleted / (double) uuids
										.size()) * 100) + "% || Time: "
								+ getSeconds() + "s || Name: " + name);
			}
			System.out.println("Finished converting the UUIDs! Completed "
					+ (int) uuidsCompleted + " names!");
			System.out
					.println(failedNames
							+ " names were unable to be converted because the UUID's were not found");
			con.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}