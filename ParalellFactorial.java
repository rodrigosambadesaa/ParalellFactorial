
/*
*
*/

import java.math.BigInteger;
import java.util.Scanner;
import java.util.stream.Stream;

// TODO: Auto-generated Javadoc
/**
* The Class ParalellFactorial.
*/
public class ParalellFactorial {

  /**
   * Adjusts a too long number to the size of the console.
   *
   * @param number     the number to be adjusted
   * @param characters the number of characters on which to break the lines
   */
  // Adjust the number when it's too long
  public static void adjustNumberToConsole(StringBuilder number, Integer characters) {
      int length = number.length();
      if (length > characters) {
          int startIndex = 0, endIndex = characters;
          while (startIndex < length) {
              StringBuilder portion = new StringBuilder(number.substring(startIndex, endIndex));
              System.out.println(portion);
              startIndex += characters;
              endIndex += characters;

              if (endIndex >= length) {
                  endIndex = length;
              }
          }
      } else {
          System.out.println(number);
      }
  }

  /**
   * The main method.
   *
   * @param args the arguments
   */
  public static void main(String[] args) {
      Scanner keyboard = new Scanner(System.in);
      BigInteger number = null;
      ParalellFactorial paralellFactorial = new ParalellFactorial();
      System.out.println("FACTORIAL OF A NUMBER");
      
      while (number == null) {
          System.out.println("Enter a positive integer:");
          try {
              String input = keyboard.next();
              number = new BigInteger(input);
              
              if (number.signum() == -1) {
                  System.out.println("Error: Please enter a non-negative integer.");
                  number = null;
                  continue;
              }
              
              long startTime = System.nanoTime();
              BigInteger result = paralellFactorial.factorial(number);
              long endTime = System.nanoTime();
              
              System.out.println("Factorial of " + number + ":");
              StringBuilder numberSb = new StringBuilder(result.toString());
              adjustNumberToConsole(numberSb, 80);
              System.out.println("Total execution time: " + (endTime - startTime) + " nanoseconds");
              System.out.println("Number of digits: " + numberSb.length());
              
          } catch (NumberFormatException e) {
              System.out.println("Error: Please enter a valid integer.");
              number = null;
          } catch (IllegalArgumentException e) {
              System.out.println("Error: " + e.getMessage());
              number = null;
          } catch (Exception e) {
              System.out.println("Error: An unexpected error occurred. Please try again.");
              number = null;
          }
      }
      keyboard.close();
  }

  /**
   * Factorial.
   *
   * @param n the number of which we want to calculate the factorial
   *
   * @return the factorial of the number
   */
  public BigInteger factorial(BigInteger n) {
      if (n == null) { 
          throw new IllegalArgumentException("The argument cannot be null"); 
      }
      if (n.signum() == -1) {
          // negative
          throw new IllegalArgumentException("Argument must be a non-negative integer");
      }
      BigInteger result;
      // For small input, iterative is faster
      if (n.compareTo(new BigInteger("9495")) <= 0) {
          result = factorialIterative(n);
      } else {
          // Stream is faster
          result = Stream.iterate(BigInteger.TWO, bi -> bi.compareTo(n) <= 0, bi -> bi.add(BigInteger.ONE)).parallel()
                  .reduce(BigInteger.ONE, BigInteger::multiply);
      }
      return result;
  }

  /**
   * Factorial iterative.
   *
   * @param n the number of which we want to calculate the factorial
   *
   * @return the factorial of the number
   */
  private BigInteger factorialIterative(BigInteger n) {
      if (n == null) { 
          throw new IllegalArgumentException(); 
      }
      if (n.signum() == -1) {
          // negative
          throw new IllegalArgumentException("Argument must be a non-negative integer");
      }
      if (n.equals(BigInteger.ZERO) || n.equals(BigInteger.ONE)) { 
          return BigInteger.ONE; 
      }
      BigInteger factorial = BigInteger.ONE;
      for (BigInteger i = new BigInteger("2"); i.compareTo(n) <= 0; i = i.add(BigInteger.ONE)) {
          factorial = factorial.multiply(i);
      }
      return factorial;
  }

}