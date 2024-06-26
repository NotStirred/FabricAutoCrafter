package com.github.tatercertified.fabricautocrafter;

public class LUTUtil {
    public static final int[] WIDTH_1_LUT = new int[] { 0, 3, 6 };
    public static final int[] WIDTH_2_LUT = new int[] { 0, 1, 3, 4, 6, 7 };
    public static final int[] WIDTH_3_LUT = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
    public static final int[][] WIDTH_LUTS = new int[][] {
            new int[] {},
            WIDTH_1_LUT,
            WIDTH_2_LUT,
            WIDTH_3_LUT,
    };
}
