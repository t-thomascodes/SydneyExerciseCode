package property;

import app.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PropertyDAO {

    static final int LIST_LIMIT = 500;

    private static final String SELECT_COLUMNS =
        "property_id, post_code, purchase_price, for_sale";

    private boolean lastResultCapped;

    public PropertyDAO() {
    }

    public boolean wasLastResultCapped() {
        return lastResultCapped;
    }

    public boolean newProperty(Property property) {
        String sql =
            "insert into property (property_id, post_code, purchase_price, for_sale) "
                + "values (?, ?, ?, ?) on conflict (property_id) do nothing";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, Long.parseLong(property.propertyID));
            statement.setString(2, property.postcode);
            statement.setLong(3, Long.parseLong(property.propertyPrice));
            statement.setBoolean(4, property.forSale);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to insert property", exception);
        }
    }

    public Optional<Property> getPropertyById(String propertyID) {
        String sql =
            "select " + SELECT_COLUMNS + " from property where property_id = ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, Long.parseLong(propertyID));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load property " + propertyID, exception);
        }
    }

    public void incrementPropertyAccessCount(String propertyID) {
        String sql =
            "update property set access_count = coalesce(access_count, 0) + 1 "
                + "where property_id = ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, Long.parseLong(propertyID));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to increment access count for property " + propertyID,
                exception
            );
        }
    }

    public Optional<Long> getPropertyAccessCount(String propertyID) {
        String sql = "select access_count from property where property_id = ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, Long.parseLong(propertyID));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getLong("access_count"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to read access count for property " + propertyID,
                exception
            );
        }
    }

    public void incrementPostCodeSearchCount(String postCode) {
        String sql =
            "insert into post_code_search_stat (post_code, search_count) values (?, 1) "
                + "on conflict (post_code) do update set search_count = "
                + "post_code_search_stat.search_count + 1";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postCode);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to increment search count for postcode " + postCode,
                exception
            );
        }
    }

    public Optional<Long> getPostCodeSearchCount(String postCode) {
        String sql = "select search_count from post_code_search_stat where post_code = ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getLong("search_count"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to read search count for postcode " + postCode,
                exception
            );
        }
    }

    public List<Property> getPropertiesByPostCode(String postCode) {
        String sql =
            "select " + SELECT_COLUMNS + " from property where post_code = ? "
                + "order by property_id limit ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postCode);
            statement.setInt(2, LIST_LIMIT + 1);
            return readCappedList(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load properties for postcode " + postCode, exception);
        }
    }

    public List<String> getAllPropertyPrices() {
        String sql = "select purchase_price from property order by property_id limit ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, LIST_LIMIT + 1);
            lastResultCapped = false;

            List<String> prices = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (prices.size() == LIST_LIMIT) {
                        lastResultCapped = true;
                        break;
                    }
                    prices.add(String.valueOf(resultSet.getLong("purchase_price")));
                }
            }
            return prices;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load property prices", exception);
        }
    }

    public List<Property> getAllProperties() {
        String sql =
            "select " + SELECT_COLUMNS + " from property order by property_id limit ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, LIST_LIMIT + 1);
            return readCappedList(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load properties", exception);
        }
    }

    public List<Property> getPropertiesByPriceRange(long minPrice, long maxPrice) {
        String sql =
            "select " + SELECT_COLUMNS + " from property "
                + "where purchase_price between ? and ? order by property_id limit ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, minPrice);
            statement.setLong(2, maxPrice);
            statement.setInt(3, LIST_LIMIT + 1);
            return readCappedList(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load properties by price range", exception);
        }
    }

    private List<Property> readCappedList(PreparedStatement statement) throws SQLException {
        lastResultCapped = false;
        List<Property> properties = new ArrayList<>();

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (properties.size() == LIST_LIMIT) {
                    lastResultCapped = true;
                    break;
                }
                properties.add(mapRow(resultSet));
            }
        }

        return properties;
    }

    private Property mapRow(ResultSet resultSet) throws SQLException {
        Property property = new Property();
        property.propertyID = String.valueOf(resultSet.getLong("property_id"));
        property.postcode = resultSet.getString("post_code");
        property.propertyPrice = String.valueOf(resultSet.getLong("purchase_price"));
        property.forSale = resultSet.getBoolean("for_sale");
        return property;
    }
}
