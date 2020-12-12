package sigra;

import java.io.BufferedReader;
import java.io.FileReader;

public class PrecomputedProbabilities {
  public static void main(String[] args) {

    try {
      // Create buffered reader using file reader with CSV file as parameter
      BufferedReader filereader =
          new BufferedReader(new FileReader("precomputed_probabilities.csv"));

      // Table format: precomputed_probabilities[numerator][denominator][power]
      // Input range: numerator: 0-52, denominator: 0-52, power: 0-10
      // Returns (numerator/denominator)^power or -1 if input denominator is 0
      int numerator_size = 53;
      int denominators_size = 53;
      int power_size = 11;
      double[][][] precomputed_probabilities =
          new double[numerator_size][denominators_size][power_size];

      // Parse CSV file and add values to table
      String curr_numerator;
      int i = 0;
      while ((curr_numerator = filereader.readLine()) != null) {
        String[] denoms = curr_numerator.substring(1, curr_numerator.length() - 1).split("\\],\\[");

        int j = 0;
        for (String denom : denoms) {
          String[] powers = denom.split(", ");

          int k = 0;
          for (String power : powers) {
            precomputed_probabilities[i][j][k] = Double.parseDouble(power);
            k = k + 1;
          }
          j = j + 1;
        }
        i = i + 1;
      }

      // System.out.println(precomputed_probabilities[4][8][2]);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
