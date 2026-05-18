package listing;

import app.DatabaseConfig;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ListingDAO {

    public long createListing(long propertyId, LocalDate listedOn, long initialPrice) {
        String insertListing =
            "insert into listing (property_id, datelisted, forsale) values (?, ?, true)";
        String insertPrice =
            "insert into listing_price (listing_id, price_date, price) values (?, ?, ?)";

        try (Connection connection = DatabaseConfig.connect()) {
            connection.setAutoCommit(false);
            try {
                long listingId;
                try (PreparedStatement listingStatement =
                         connection.prepareStatement(insertListing, Statement.RETURN_GENERATED_KEYS)) {
                    listingStatement.setLong(1, propertyId);
                    listingStatement.setDate(2, Date.valueOf(listedOn));
                    listingStatement.executeUpdate();
                    try (ResultSet keys = listingStatement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new IllegalStateException("insert into listing did not return listing_id");
                        }
                        listingId = keys.getLong(1);
                    }
                }

                try (PreparedStatement priceStatement = connection.prepareStatement(insertPrice)) {
                    priceStatement.setLong(1, listingId);
                    priceStatement.setDate(2, Date.valueOf(listedOn));
                    priceStatement.setLong(3, initialPrice);
                    priceStatement.executeUpdate();
                }

                connection.commit();
                return listingId;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create listing", exception);
        }
    }

    public void addPrice(long listingId, LocalDate effectiveDate, long price) {
        String sql =
            "insert into listing_price (listing_id, price_date, price) values (?, ?, ?)";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, listingId);
            statement.setDate(2, Date.valueOf(effectiveDate));
            statement.setLong(3, price);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add listing price", exception);
        }
    }

    public Optional<ListingDetail> getListing(long listingId) {
        String listingSql =
            "select listing_id, property_id, datelisted from listing where listing_id = ?";
        String pricesSql =
            "select price_date, price from listing_price where listing_id = ? "
                + "order by price_date asc, price_id asc";

        try (Connection connection = DatabaseConfig.connect()) {
            ListingDetail detail;
            try (PreparedStatement statement = connection.prepareStatement(listingSql)) {
                statement.setLong(1, listingId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    detail = new ListingDetail();
                    detail.setListingID(String.valueOf(resultSet.getLong("listing_id")));
                    detail.setPropertyID(String.valueOf(resultSet.getLong("property_id")));
                    detail.setListedOn(resultSet.getDate("datelisted").toLocalDate().toString());
                }
            }

            List<ListingPricePoint> prices = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(pricesSql)) {
                statement.setLong(1, listingId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String date = resultSet.getDate("price_date").toLocalDate().toString();
                        String price = String.valueOf(resultSet.getLong("price"));
                        prices.add(new ListingPricePoint(date, price));
                    }
                }
            }
            detail.setPrices(prices);
            return Optional.of(detail);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load listing " + listingId, exception);
        }
    }

    public List<ListingDetail> getListingsForProperty(long propertyId) {
        String idsSql = "select listing_id from listing where property_id = ? order by listing_id";
        List<Long> ids = new ArrayList<>();

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(idsSql)) {

            statement.setLong(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getLong("listing_id"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list listings for property " + propertyId, exception);
        }

        List<ListingDetail> out = new ArrayList<>();
        for (Long id : ids) {
            getListing(id).ifPresent(out::add);
        }
        return out;
    }
}
