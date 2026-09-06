package com.dwurdy.heaphammer.detection;

/**
 * Ordinary Least Squares (OLS) linear regression model.
 */
public record LinearRegression(double slope, double intercept, double rSquared, double standardError) {

    public LinearRegression(double slope, double intercept, double rSquared) {
        this(slope, intercept, rSquared, 0.0);
    }

    public double predict(double x) {
        return slope * x + intercept;
    }

    public static LinearRegression compute(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y arrays must have identical length");
        }
        int n = x.length;
        if (n < 2) {
            return new LinearRegression(0.0, n > 0 ? y[0] : 0.0, 0.0, 0.0);
        }

        double sumX = 0;
        double sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        double xxBar = 0;
        double xyBar = 0;
        for (int i = 0; i < n; i++) {
            xxBar += (x[i] - meanX) * (x[i] - meanX);
            xyBar += (x[i] - meanX) * (y[i] - meanY);
        }

        if (Math.abs(xxBar) < 1e-12) {
            return new LinearRegression(0.0, meanY, 0.0, 0.0);
        }

        double slope = xyBar / xxBar;
        double intercept = meanY - slope * meanX;

        double ssTot = 0;
        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double fit = slope * x[i] + intercept;
            ssTot += (y[i] - meanY) * (y[i] - meanY);
            ssRes += (y[i] - fit) * (y[i] - fit);
        }

        double rSquared = (ssTot > 1e-12) ? Math.max(0.0, Math.min(1.0, 1.0 - (ssRes / ssTot))) : 1.0;
        double standardError = (n > 2 && xxBar > 1e-12) ? Math.sqrt((ssRes / (n - 2)) / xxBar) : 0.0;

        return new LinearRegression(slope, intercept, rSquared, standardError);
    }
}
