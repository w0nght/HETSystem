package agents;

/**
 * @author Hei @ Asus on 1/11/2018
 */
public class linearRA extends RetailerAgent {
    @Override
    public float getOfferPrice(int usage) {
        int minPrice = 3;
        int gradient = 14;

        return gradient * usage + minPrice;
    }
}
