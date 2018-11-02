package fml;

/**
 * @author Asus on 1/11/2018
 * @project EenergyTradingSystem
 * @package fml
 */
public class linearRA extends retailagent {
    @Override
    public double getOfferPrice(double usage) {
        double minPrice = 3;
        double gradient = 14;

        return gradient * usage + minPrice;
    }
}
