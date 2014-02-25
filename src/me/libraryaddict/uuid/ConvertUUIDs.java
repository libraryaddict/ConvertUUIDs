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

import com.mojang.api.profiles.HttpProfileRepository;
import com.mojang.api.profiles.Profile;
import com.mojang.api.profiles.ProfileCriteria;

public class ConvertUUIDs {

    private static final String AGENT = "minecraft";
    private static Connection con;
    private static String mysql_Database = "Database";
    private static String mysql_IP = "url.or.ip.to.mydatabase.com";
    private static String mysql_Password = "password";
    private static String mysql_Player_Column = "Name";
    private static String mysql_Table = "TableName";
    private static String mysql_User = "root";
    private static String mysql_UUID_Column = "UUID";

    private static final HttpProfileRepository profileRepository = new HttpProfileRepository();

    private static Connection connectMysql() {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            String conn = "jdbc:mysql://" + mysql_IP + "/" + mysql_Database;
            return DriverManager.getConnection(conn, mysql_User, mysql_Password);
        } catch (ClassNotFoundException ex) {
            System.err.println("[ConvertUUIDs] No MySQL driver found!");
        } catch (SQLException ex) {
            System.err.println("[ConvertUUIDs] Error while fetching MySQL connection!");
        } catch (Exception ex) {
            System.err.println("[ConvertUUIDs] Unknown error while fetching MySQL connection.");
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
        Profile[] profiles = profileRepository.findProfilesByCriteria(new ProfileCriteria(name, AGENT));
        if (profiles.length > 0) {
            return profiles[0].getId();
        } else {
            return null;
        }
    }

    private static String getSeconds() {
        if (!perSecond.isEmpty()) {
            perSecond.set(perSecond.size() - 1, System.currentTimeMillis() - perSecond.get(perSecond.size() - 1));
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

    private static ArrayList<Long> perSecond = new ArrayList<Long>();

    public static void main(String[] args) {
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
                    "SELECT " + mysql_Player_Column + " FROM " + mysql_Table + " WHERE " + mysql_UUID_Column + "=''");
            ResultSet r = stmt.executeQuery();
            r.beforeFirst();
            HashSet<String> uuids = new HashSet<String>();
            while (r.next()) {
                uuids.add(r.getString("Name"));
            }
            System.out.println("Found " + uuids.size() + " names to do");
            stmt.close();
            double uuidsCompleted = 0;
            int failedNames = 0;
            stmt = getConnection().prepareStatement(
                    "UPDATE " + mysql_Table + " SET " + mysql_UUID_Column + "=? WHERE `" + mysql_Player_Column + "` = ?");
            Iterator<String> itel = uuids.iterator();
            while (itel.hasNext()) {
                String name = itel.next();
                String uuid = getUUID(name);
                if (uuid == null) {
                    System.out.println("Cannot fetch UUID for player " + name + ". Skipping him!");
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
                System.out.println("Status: " + (int) uuidsCompleted++ + "/" + uuids.size() + " || "
                        + (int) ((uuidsCompleted / (double) uuids.size()) * 100) + "% || Time: " + getSeconds() + "s || Name: "
                        + name);
            }
            System.out.println("Finished converting the UUIDs! Completed " + (int) uuidsCompleted + " names!");
            System.out.println(failedNames + " names were unable to be converted because the UUID's were not found");
            con.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
