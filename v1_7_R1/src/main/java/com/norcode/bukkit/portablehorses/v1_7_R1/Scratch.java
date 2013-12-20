package com.norcode.bukkit.portablehorses.v1_7_R1;

import java.text.DecimalFormat;

/**
 * Created with IntelliJ IDEA.
 * User: andre
 * Date: 13/12/13
 * Time: 6:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class Scratch {
	static double[] d = new double[] {22.5, 22.14, 22.99999, 100.4, 100, 30, 0.5};
	public static void main(String ... args) {
		for (double n: d) {
			System.out.println(new DecimalFormat("0.#####").format(n));
		}
	}

}
