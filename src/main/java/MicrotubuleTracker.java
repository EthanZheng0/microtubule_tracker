/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the Unlicense for details:
 *     https://unlicense.org/
 */

import io.scif.services.DatasetIOService;

import java.io.File;
import java.io.IOException;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Adds two datasets using the ImgLib2 framework.
 */
@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Microtubule Tracker")
public class MicrotubuleTracker implements Command, Previewable {
	
	private final double PIXEL_MAX = 65535;
	private final double PIXEL_MIN = 0;
	
	private final int[][] structSquare = {
			{-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, 
			{1, 1}, {1, 0}, {1, -1}, {0, -1}};
	private final int[][] structCross = {
			{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
	
	private int width = 0;
	private int height = 0;
	private int depth = 0;
	
	private Dataset dataset;
	private double[][][] stack;

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private DatasetIOService datasetIOService;

	@Parameter
	private OpService opService;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String header = "Choose a microtubule image stack";

	@Parameter(label = "file")
	private File file;

	@Parameter(label = "Result", type = ItemIO.OUTPUT)
	private Dataset result;

	@Override
	public void run() {
		try {
			dataset = datasetIOService.open(file.getAbsolutePath());
		}
		catch (final IOException e) {
			log.error(e);
			return;
		}
		width = (int) dataset.dimension(0);
		height = (int) dataset.dimension(1);
		depth = (int) dataset.dimension(2);
		
		unpack();
		posterizeWithAveraging(10);
		open(structSquare, 1);
		dilate(structCross, 1);
		result = pack();
	}

	@Override
	public void cancel() {
		log.info("Cancelled");
	}

	@Override
	public void preview() {
		log.info("previews Microtubule Tracker");
		statusService.showStatus(header);
	}
	
	@SuppressWarnings("unused")
	private void printStack() {
		System.out.println("---- PRINT STACK ----");
		for(int i = 0; i < depth; ++i) {
			System.out.println(stack[i][200][200]);
		}
	}
	
	private double[][] deepCopy(double[][] src) {
		double[][] tgt = new double[src.length][src[0].length];
		for(int r = 0; r < src.length; ++r) {
			for(int c = 0; c < src[0].length; ++c) {
				tgt[r][c] = src[r][c];
			}
		}
		return tgt;
	}
	
	@SuppressWarnings("rawtypes")
	private void unpack() {
		stack = new double[depth][height][width];
		final RandomAccess<? extends RealType> ra = dataset.getImgPlus().randomAccess();
		final Cursor<? extends RealType> cursor = dataset.getImgPlus().localizingCursor();
		final long[] pos = new long[dataset.numDimensions()];
		
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(pos);
			ra.setPosition(pos);
			int w = (int) pos[0];
			int h = (int) pos[1];
			int d = (int) pos[2];
			stack[d][h][w] = ra.get().getRealDouble();
		}
	}
	
	@SuppressWarnings("rawtypes")
	private Dataset pack() {
		final Dataset packed = dataset.duplicateBlank();
		final Cursor<? extends RealType> cursor = packed.getImgPlus().localizingCursor();
		final long[] pos = new long[dataset.numDimensions()];
		
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(pos);
			int w = (int) pos[0];
			int h = (int) pos[1];
			int d = (int) pos[2];
			cursor.get().setReal(stack[d][h][w]);
		}
		return packed;
	}
	
	private void invert() {
		for(int d = 0; d < depth; ++d) {
			for(int h = 0; h < height; ++h) {
				for(int w = 0; w < width; ++w) {
					stack[d][h][w] = PIXEL_MAX - stack[d][h][w];
				}
			}
		}
	}
	
	private void posterize(double threshold) {
		if(threshold < PIXEL_MIN || threshold > PIXEL_MAX) return;
		for(int d = 0; d < depth; ++d) {
			for(int h = 0; h < height; ++h) {
				for(int w = 0; w < width; ++w) {
					stack[d][h][w] = stack[d][h][w] > threshold ? PIXEL_MAX : PIXEL_MIN;
				}
			}
		}
	}
	
	private void posterizeWithAveraging(int diameter) {
		double[][][] blurred = new double[depth][height][width];
		for(int d = 0; d < depth; ++d) {
			blurred[d] = average(diameter, d);
		}
		for(int d = 0; d < depth; ++d) {
			for(int h = 0; h < height; ++h) {
				for(int w = 0; w < width; ++w) {
					stack[d][h][w] = stack[d][h][w] > blurred[d][h][w] ? PIXEL_MAX : PIXEL_MIN;
				}
			}
		}
	}
	
	private void blur(int diameter) {
		for(int d = 0; d < depth; ++d) {
			stack[d] = average(diameter, d);
		}
	}
	
	private double[][] average(int diameter, int frame) {
		System.out.println("Averaging: " + frame);
		double[][] avg = new double[stack[frame].length][stack[frame][0].length];
		for(int h = 0; h < height; ++h) {
			for(int w = 0; w < width; ++w) {
				double sum = 0;
				int top = Math.max(h - diameter, 0);
				int bottom = Math.min(h + diameter, height - 1);
				int left = Math.max(w - diameter, 0);
				int right = Math.min(w + diameter, width - 1);
				for(int hh = top; hh <= bottom; ++hh) {
					for(int ww = left; ww <= right; ++ww) {
						sum += stack[frame][hh][ww];
					}
				}
				avg[h][w] = sum / ((bottom - top + 1) * (right - left + 1));
			}
		}
		return avg;
	}
	
	private void erode(int[][] struct, int round) {
		for(int d = 0; d < depth; ++d) {
			double[][] slice = deepCopy(stack[d]);
			for(int r = 0; r < round; ++r) {
				double[][] newSlice = deepCopy(slice);
				for(int h = 0; h < height; ++h) {
					for(int w = 0; w < width; ++w) {
						if(slice[h][w] == PIXEL_MIN) {
							for(int k = 0; k < struct.length; ++k) {
								int hh = h + struct[k][0];
								int ww = w + struct[k][1];
								if(hh >= 0 && hh < height && ww >= 0 && ww < width) {
									newSlice[hh][ww] = PIXEL_MIN;
								}
							}
						}
					}
				}
				slice = newSlice;
			}
			stack[d] = slice;
		}
	}
	
	private void dilate(int[][] struct, int round) {
		invert();
		erode(struct, round);
		invert();
	}
	
	private void open(int[][] struct, int round) {
		erode(struct, round);
		dilate(struct, round);
	}
	
	private void close(int[][] struct, int round) {
		dilate(struct, round);
		erode(struct, round);
	}
	
	public static void main(final String... args) throws Exception {
		// Create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.launch(args);
		ij.command().run(MicrotubuleTracker.class, true);
	}

}
