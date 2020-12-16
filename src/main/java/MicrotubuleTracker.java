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
		// add them together
		Dataset dataset;
		try {
			dataset = datasetIOService.open(file.getAbsolutePath());
		}
		catch (final IOException e) {
			log.error(e);
			return;
		}
		
		result = posterize(dataset, 15000);
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
	
	@SuppressWarnings({ "rawtypes" })
	private Dataset invert(Dataset d) {
		final Dataset result = d.duplicateBlank();
		final RandomAccess<? extends RealType> ra = d.getImgPlus().randomAccess();
		final Cursor<? extends RealType> cursor = result.getImgPlus().localizingCursor();
		final long[] pos = new long[d.numDimensions()];
		
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(pos);
			ra.setPosition(pos);
			final double val = 65535 - ra.get().getRealDouble();
			cursor.get().setReal(val);
		}
		
		return result;
	}
	
	@SuppressWarnings({ "rawtypes" })
	private Dataset posterize(Dataset d, double threshold) {
		if(threshold < 0 || threshold > 65535) return d;
		final Dataset result = d.duplicateBlank();
		final RandomAccess<? extends RealType> ra = d.getImgPlus().randomAccess();
		final Cursor<? extends RealType> cursor = result.getImgPlus().localizingCursor();
		final long[] pos = new long[d.numDimensions()];
		
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(pos);
			ra.setPosition(pos);
			final double val = ra.get().getRealDouble();
			cursor.get().setReal(val > threshold ? 65535 : 0);
		}
		
		return result;
	}

	public static void main(final String... args) throws Exception {
		// Create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.launch(args);
		ij.command().run(MicrotubuleTracker.class, true);
	}

}
