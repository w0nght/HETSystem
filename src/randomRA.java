package fml;

/**
 * @author Asus on 1/11/2018
 * @project EenergyTradingSystem
 * @package fml
 */
public class randomRA extends retailagent {

    private double minPrice = 10;
    private double maxPrice = 30;

    @Override
    public double getOfferPrice(double usage) {
        double price = (int) (Math.random() * (maxPrice - minPrice)) + minPrice;
        return price * usage;

    }
}
