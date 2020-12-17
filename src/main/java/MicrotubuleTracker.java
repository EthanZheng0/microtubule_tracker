/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the Unlicense for details:
 *     https://unlicense.org/
 */

import java.io.File;
import java.io.IOException;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.display.event.input.MsClickedEvent;
import org.scijava.event.EventHandler;
import org.scijava.event.SciJavaEvent;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.DrawingTool;
import net.imagej.ImageJ;
import net.imagej.display.ImageDisplayService;
import net.imagej.display.event.AxisPositionEvent;
import net.imagej.ops.OpService;
import net.imagej.render.RenderingService;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;

@SuppressWarnings({"unused", "rawtypes"})
@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Microtubule Tracker")
public class MicrotubuleTracker implements Command, Previewable {
	
	private final double WHITE = 65535;
	private final double BLACK = 0;
	
	private final int[][] structSquare = {
			{-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, 
			{1, 1}, {1, 0}, {1, -1}, {0, -1}};
	private final int[][] structCross = {
			{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
	
	private int width = 0;
	private int height = 0;
	private int depth = 0;
	
	private int currentFrame = 0;
	private int[] mouseClick1 = new int[2];
	private int[] mouseClick2 = new int[2];
	private boolean mouseClickSet = false;
	
	private Dataset dataset;
	private double[][][] stack;

	@Parameter
	private static LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private DatasetIOService datasetIOService;
	
	@Parameter
	private ImageDisplayService imageDisplayService;
	
	@Parameter
	private RenderingService renderingService;

	@Parameter
	private OpService opService;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String header = "Choose a microtubule image stack";

	@Parameter(label = "file")
	private File file;

	@Parameter(label = "Result", type = ItemIO.OUTPUT)
	private Dataset result;
	
	@EventHandler
	public void onEvent(final MsClickedEvent evt) {
		System.out.println("Mouse clicked:");
		System.out.println("X: " + evt.getX());
		System.out.println("Y: " + evt.getY());
		System.out.println("Z: " + currentFrame);
		
		final DrawingTool tool = new DrawingTool(result, renderingService);
		tool.setPosition(new long[] {0, 0, currentFrame});
		if(mouseClickSet) {
			mouseClick2[0] = evt.getX();
			mouseClick2[1] = evt.getY();
			tool.setLineWidth(2);
			tool.drawLine(mouseClick1[0], mouseClick1[1], mouseClick2[0], mouseClick2[1]);
			mouseClickSet = false;
			System.out.println("MouseClick2");
		}
		else {
			mouseClick1[0] = evt.getX();
			mouseClick1[1] = evt.getY();
			mouseClickSet = true;
			System.out.println("MouseClick1");
		}
		result.update();

	}

	@EventHandler
	public void onEvent(final AxisPositionEvent evt) {
		currentFrame = imageDisplayService.getActiveDatasetView().getIntPosition(evt.getAxis());
	}
	
	private void logEvent(final SciJavaEvent evt) {
		log.info("[" + evt.getClass().getSimpleName() + "] " + evt);
	}
	
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
		
		result = dataset;
		
		processImage();
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
	
	private void processImage() {
		unpack();
		
		posterizeWithAveraging(10);
		cleanNoisePerFrame();
		cleanNoiseAcrossFrames(1);
		cleanNoisePerFrame();
		cleanNoiseAcrossFrames(1);
		cleanNoisePerFrame();
		cleanNoiseAcrossFrames(1);
		dilate(structCross, 1);
		connectComponent(2);

		result = pack();
	}
	
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
					stack[d][h][w] = WHITE - stack[d][h][w];
				}
			}
		}
	}
	
	private void posterize(double threshold) {
		if(threshold < BLACK || threshold > WHITE) return;
		for(int d = 0; d < depth; ++d) {
			for(int h = 0; h < height; ++h) {
				for(int w = 0; w < width; ++w) {
					stack[d][h][w] = stack[d][h][w] > threshold ? WHITE : BLACK;
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
					stack[d][h][w] = stack[d][h][w] > blurred[d][h][w] + 1024 ? WHITE : BLACK;
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
						if(slice[h][w] == BLACK) {
							for(int k = 0; k < struct.length; ++k) {
								int hh = h + struct[k][0];
								int ww = w + struct[k][1];
								if(hh >= 0 && hh < height && ww >= 0 && ww < width) {
									newSlice[hh][ww] = BLACK;
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
	
	private void cleanNoiseAcrossFrames(int round) {
		for(int r = 0; r < round; ++r) {
			System.out.println("Clean Noise: " + r);
			double[][][] newStack = new double[depth][1][1];
			newStack[0] = stack[0];
			newStack[depth - 1] = stack[depth - 1];
			for(int d = 0; d < depth; ++d) {
				double[][] newFrame = deepCopy(stack[d]);
				for(int h = 0; h < height; ++h) {
					for(int w = 0; w < width; ++w) {
						if(d == 0) {
							if(stack[d + 1][h][w] == BLACK) {
								newFrame[h][w] = BLACK;
							}
						}
						else if(d == depth - 1) {
							if(stack[d - 1][h][w] == BLACK) {
								newFrame[h][w] = BLACK;
							}
						}
						else if(stack[d - 1][h][w] == BLACK && stack[d + 1][h][w] == BLACK) {
							newFrame[h][w] = BLACK;
						}
					}
				}
				newStack[d] = newFrame;
			}
			stack = newStack;
		}
	}
	
	private void cleanNoisePerFrame() {
		for(int d = 0; d < depth; ++d) {
			double[][] newFrame = deepCopy(stack[d]);
			for(int h = 0; h < height; ++h) {
				for(int w = 0; w < width; ++w) {
					if(stack[d][h][w] == WHITE) {
						int count = 0;
						if(h == 0 || stack[d][h - 1][w] == BLACK) count++;
						if(h == height - 1 || stack[d][h + 1][w] == BLACK) count++;
						if(w == 0 || stack[d][h][w - 1] == BLACK) count++;
						if(w == width - 1 || stack[d][h][w + 1] == BLACK) count++;
						if(count > 2) newFrame[h][w] = BLACK;
					}
				}
			}
			stack[d] = newFrame;
		}
	}
	
	private void connectComponent(int round) {
		for(int r = 0; r < round; ++r) {
			System.out.println("Connect Compnent: " + r);
			double[][][] newStack = new double[depth][1][1];
			newStack[0] = stack[0];
			newStack[depth - 1] = stack[depth - 1];
			for(int d = 1; d < depth - 1; ++d) {
				double[][] newFrame = deepCopy(stack[d]);
				for(int h = 0; h < height; ++h) {
					for(int w = 0; w < width; ++w) {
						if(stack[d - 1][h][w] == WHITE && stack[d + 1][h][w] == WHITE) {
							newFrame[h][w] = WHITE;
						}
					}
				}
				newStack[d] = newFrame;
			}
			stack = newStack;
		}
	}
	
	public static void main(final String... args) throws Exception {
		// Create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.launch(args);
		ij.command().run(MicrotubuleTracker.class, true);
	}

}
