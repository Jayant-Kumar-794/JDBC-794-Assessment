import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class BankTransferApplication {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/bank_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Jai_skpk21";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Bank Transfer Portal ===");
        System.out.print("Enter sender account number: ");
        int senderAccount = scanner.nextInt();

        System.out.print("Enter receiver account number: ");
        int receiverAccount = scanner.nextInt();

        System.out.print("Enter amount to transfer: ");
        double amount = scanner.nextDouble();

        if (amount <= 0) {
            System.out.println("Transfer failed: Amount must be greater than zero.");
            scanner.close();
            return;
        }

        if (senderAccount == receiverAccount) {
            System.out.println("Transfer failed: Sender and receiver cannot be the same account.");
            scanner.close();
            return;
        }

        transferMoney(senderAccount, receiverAccount, amount);
        scanner.close();
    }

    public static void transferMoney(int senderAccount, int receiverAccount, double amount) {
        String checkBalanceSql = "SELECT balance FROM accounts WHERE account_number = ?";
        String checkReceiverSql = "SELECT account_number FROM accounts WHERE account_number = ?";
        String debitSql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ?";
        String creditSql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";

        Connection connection = null;

        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            connection.setAutoCommit(false);

            double senderBalance = 0;
            boolean senderExists = false;

            try (PreparedStatement checkSenderStmt = connection.prepareStatement(checkBalanceSql)) {
                checkSenderStmt.setInt(1, senderAccount);
                try (ResultSet rs = checkSenderStmt.executeQuery()) {
                    if (rs.next()) {
                        senderExists = true;
                        senderBalance = rs.getDouble("balance");
                    }
                }
            }

            if (!senderExists) {
                System.out.println("Transfer failed: Sender account does not exist.");
                connection.rollback();
                return;
            }

            if (senderBalance < amount) {
                System.out.printf("Transfer failed: Insufficient funds. Available balance: %.2f\n", senderBalance);
                connection.rollback();
                return;
            }

            boolean receiverExists = false;
            try (PreparedStatement checkReceiverStmt = connection.prepareStatement(checkReceiverSql)) {
                checkReceiverStmt.setInt(1, receiverAccount);
                try (ResultSet rs = checkReceiverStmt.executeQuery()) {
                    if (rs.next()) {
                        receiverExists = true;
                    }
                }
            }

            if (!receiverExists) {
                System.out.println("Transfer failed: Receiver account does not exist.");
                connection.rollback();
                return;
            }

            try (PreparedStatement debitStmt = connection.prepareStatement(debitSql)) {
                debitStmt.setDouble(1, amount);
                debitStmt.setInt(2, senderAccount);
                debitStmt.executeUpdate();
            }

            try (PreparedStatement creditStmt = connection.prepareStatement(creditSql)) {
                creditStmt.setDouble(1, amount);
                creditStmt.setInt(2, receiverAccount);
                creditStmt.executeUpdate();
            }

            connection.commit();
            System.out.printf("Transfer successful: Transferred %.2f from account %d to account %d.\n", 
                              amount, senderAccount, receiverAccount);

        } catch (SQLException e) {
            System.err.println("Transaction encountered an error: " + e.getMessage());
            if (connection != null) {
                try {
                    connection.rollback();
                    System.err.println("Transaction rolled back successfully.");
                } catch (SQLException rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException closeEx) {
                    System.err.println("Error closing connection: " + closeEx.getMessage());
                }
            }
        }
    }
}