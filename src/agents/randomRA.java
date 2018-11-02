package agents;

/**
 * @author HeiTung @ Asus on 26/10/2018
 */
public class randomRA extends RetailerAgent {

    private int minPrice = (int) (Math.random() * 2);
    private int maxPrice = minPrice + 20;

    @Override
    public float getOfferPrice(int usage) {
        int price = (int) (Math.random() * (maxPrice - minPrice)) + minPrice;
        return price * usage;

    }
}
