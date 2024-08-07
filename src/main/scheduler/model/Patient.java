package scheduler.model;

import scheduler.db.ConnectionManager;
import scheduler.util.Util;

import java.sql.*;
import java.util.Arrays;

public class Patient {
    private final String username;
    private final byte[] salt;
    private final byte[] hash;

    private Patient(Patient.PatientBuilder builder) {
        this.username = builder.username;
        this.salt = builder.salt;
        this.hash = builder.hash;
    }

    private Patient(Patient.PatientGetter getter) {
        this.username = getter.username;
        this.salt = getter.salt;
        this.hash = getter.hash;
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public byte[] getSalt() {
        return salt;
    }

    public byte[] getHash() {
        return hash;
    }

    public void saveToDB() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String addPatient = "INSERT INTO Patients VALUES (? , ?, ?)";
        try {
            PreparedStatement statement = con.prepareStatement(addPatient);
            statement.setString(1, this.username);
            statement.setBytes(2, this.salt);
            statement.setBytes(3, this.hash);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public void reserve(Date d, String vaccineName) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            // Find the first available caregiver for the given date
            String findAvailability = "SELECT Username FROM Availabilities WHERE Time = ?";
            PreparedStatement statement = con.prepareStatement(findAvailability);
            statement.setDate(1, d);
            ResultSet caregivers = statement.executeQuery();
            String firstCaregiver = "";
            if(caregivers.next()) {
                firstCaregiver = caregivers.getString("Username");
            } else {
                System.out.println("No available caregivers for this date!");
                return;
            }
            // Check if the vaccine storage contains the vaccine
            String vaccine = " ";
            PreparedStatement checkVaccine = con.prepareStatement("SELECT Name FROM Vaccines WHERE Name = ?");
            checkVaccine.setString(1, vaccineName);
            ResultSet vaccines = checkVaccine.executeQuery();
            if(vaccines.next()) {
                vaccine = vaccines.getString("Name");
            }
            if(!vaccine.equals(vaccineName)) {
                System.out.println("Our vaccine storage does not have this vaccine!");
                return;
            }
            // Find the number of available doses for the given vaccine
            int availableDoses = 0;
            PreparedStatement findAvailableDoses = con.prepareStatement("SELECT Doses FROM Vaccines WHERE Name = ?");
            findAvailableDoses.setString(1, vaccineName);
            ResultSet doses = findAvailableDoses.executeQuery();
            if(doses.next()) {
                availableDoses = doses.getInt("Doses");
            }
            if (availableDoses > 0) {
                // Book the appointment
                String reserveQuery = "INSERT INTO Appointments VALUES (?, ? , ?, ?)";
                PreparedStatement reserveAppointment = con.prepareStatement(reserveQuery);
                reserveAppointment.setDate(1, d);
                reserveAppointment.setString(2, vaccineName);
                reserveAppointment.setString(3, firstCaregiver);
                reserveAppointment.setString(4, this.username);
                reserveAppointment.executeUpdate();

                // Update vaccine doses
                PreparedStatement updateVaccineDoses = con.prepareStatement("UPDATE vaccines SET Doses = ? WHERE name = ?");
                updateVaccineDoses.setInt(1, availableDoses - 1);
                updateVaccineDoses.setString(2, vaccineName);
                updateVaccineDoses.executeUpdate();

                // Delete availability for this caregiver on the given date
                PreparedStatement updateAvailability = con.prepareStatement("DELETE FROM Availabilities WHERE Time = ? AND Username = ?");
                updateAvailability.setDate(1, d);
                updateAvailability.setString(2, firstCaregiver);
                updateAvailability.executeUpdate();
                System.out.println("Appointment reserved!");
            } else {
                System.out.println("No available doses for this vaccine!");
                return;
            }
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }


    public static class PatientBuilder {
        private final String username;
        private final byte[] salt;
        private final byte[] hash;

        public PatientBuilder(String username, byte[] salt, byte[] hash) {
            this.username = username;
            this.salt = salt;
            this.hash = hash;
        }

        public Patient build() {
            return new Patient(this);
        }
    }

    public static class PatientGetter {
        private final String username;
        private final String password;
        private byte[] salt;
        private byte[] hash;

        public PatientGetter(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public Patient get() throws SQLException {
            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();

            String getPatient = "SELECT Salt, Hash FROM Patients WHERE Username = ?";
            try {
                PreparedStatement statement = con.prepareStatement(getPatient);
                statement.setString(1, this.username);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    byte[] salt = resultSet.getBytes("Salt");
                    // we need to call Util.trim() to get rid of the paddings,
                    // try to remove the use of Util.trim() and you'll see :)
                    byte[] hash = Util.trim(resultSet.getBytes("Hash"));
                    // check if the password matches
                    byte[] calculatedHash = Util.generateHash(password, salt);
                    if (!Arrays.equals(hash, calculatedHash)) {
                        return null;
                    } else {
                        this.salt = salt;
                        this.hash = hash;
                        return new Patient(this);
                    }
                }
                return null;
            } catch (SQLException e) {
                throw new SQLException();
            } finally {
                cm.closeConnection();
            }
        }
    }
}
