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
		posterize(15000);
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

	public static void main(final String... args) throws Exception {
		// Create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.launch(args);
		ij.command().run(MicrotubuleTracker.class, true);
	}

}
