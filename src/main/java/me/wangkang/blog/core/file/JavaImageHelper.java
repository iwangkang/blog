/*
 * Copyright 2017 wangkang.me
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.wangkang.blog.core.file;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Function;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.madgag.gif.fmsware.GifDecoder;

import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.util.FileUtils;

/**
 * 基于java的图片处理
 * <p>
 * <b>这个类仅供测试使用，请勿在实际项目中使用
 * </p>
 * 
 * @see GraphicsMagickImageHelper
 * @author Administrator
 *
 */
public class JavaImageHelper extends ImageHelper {

	private static final WhiteBgFilter WHITE_BG_FILTER = new WhiteBgFilter();

	@Override
	protected void doResize(Resize resize, Path src, Path dest) throws IOException {
		String ext = FileUtils.getFileExtension(src);
		Path todo = src;
		Path tmp = null;
		if (isGIF(ext)) {
			// 获取封面
			tmp = FileUtils.appTemp(PNG);
			doGetGifCover(src, tmp);
			todo = tmp;
		}
		BufferedImage bi = doResize(todo, dest, resize);
		writeImg(bi, FileUtils.getFileExtension(dest), dest.toFile());
	}

	@Override
	protected ImageInfo doRead(Path file) throws IOException {
		String ext = FileUtils.getFileExtension(file);
		if (isGIF(ext)) {
			return readGif(file);
		} else {
			return readOtherImage(file);
		}
	}

	private ImageInfo readGif(Path file) throws IOException {
		try (InputStream is = Files.newInputStream(file)) {
			GifDecoder gd = new GifDecoder();
			int flag = gd.read(is);
			if (flag != GifDecoder.STATUS_OK) {
				throw new IOException(file + "文件无法读取");
			}
			Dimension dim = gd.getFrameSize();
			return new ImageInfo(dim.width, dim.height, GIF);
		}
	}

	private ImageInfo readOtherImage(Path file) throws IOException {
		try (InputStream is = Files.newInputStream(file)) {
			try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
				Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);
				while (imageReaders.hasNext()) {
					ImageReader reader = imageReaders.next();
					reader.setInput(iis);
					int minIndex = reader.getMinIndex();
					return new ImageInfo(reader.getWidth(minIndex), reader.getHeight(minIndex), reader.getFormatName());
				}
				throw new IOException("无法确定图片:" + file + "的具体类型");
			}
		}
	}

	private void doGetGifCover(Path gif, Path dest) throws IOException {
		Path png = null;
		try (InputStream is = Files.newInputStream(gif)) {
			GifDecoder gd = new GifDecoder();
			int flag = gd.read(is);
			if (flag != GifDecoder.STATUS_OK) {
				throw new IOException(gif + "文件无法读取");
			}
			BufferedImage bi = gd.getFrame(0);
			png = FileUtils.appTemp(PNG);
			writeImg(bi, PNG, png.toFile());
			String destExt = FileUtils.getFileExtension(dest);
			if (isPNG(destExt)) {
				try {
					FileUtils.deleteQuietly(dest);
					FileUtils.move(png, dest);
					return;
				} catch (IOException e) {
					throw new SystemException(e.getMessage(), e);
				}
			}
			// PNG to Other Format
			BufferedImage readed = ImageIO.read(png.toFile());
			writeImg(WHITE_BG_FILTER.apply(readed), destExt, dest.toFile());
		}
	}

	private synchronized void writeImg(BufferedImage bi, String ext, File dest) throws IOException {
		FileUtils.deleteQuietly(dest.toPath());
		ImageIO.write(bi, ext, dest);
		bi.flush();
	}

	private BufferedImage doResize(Path todo, Path dest, Resize resize) throws IOException {
		BufferedImage originalImage = ImageIO.read(todo.toFile());
		int width = originalImage.getWidth();
		int height = originalImage.getHeight();
		int resizeWidth;
		int resizeHeight;
		if (resize.getSize() != null) {
			int size = resize.getSize();
			if (width > height) {
				resizeWidth = size > width ? width : size;
				resizeHeight = resizeWidth * height / width;
			} else if (width < height) {
				resizeHeight = size > height ? height : size;
				resizeWidth = resizeHeight * width / height;
			} else {
				resizeWidth = resizeHeight = size > width ? width : size;
			}
		} else {
			if (resize.isKeepRatio()) {
				return doResize(todo, dest, new Resize(Math.max(resize.getWidth(), resize.getHeight())));
			} else {
				resizeWidth = (resize.getWidth() > width) ? width : resize.getWidth();
				resizeHeight = (resize.getHeight() > height) ? height : resize.getHeight();
			}
		}
		String destExt = FileUtils.getFileExtension(dest);

		boolean maybeTransparentBy = maybeTransparentBg(destExt);
		int imageType = maybeTransparentBy ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage scaledBI = new BufferedImage(resizeWidth, resizeHeight, imageType);
		Graphics2D g = scaledBI.createGraphics();
		if (!maybeTransparentBy) {
			g.setComposite(AlphaComposite.Src);
			g.drawImage(originalImage, 0, 0, resizeWidth, resizeHeight, Color.WHITE, null);
		} else {
			g.drawImage(originalImage, 0, 0, resizeWidth, resizeHeight, null);
		}
		g.dispose();

		return scaledBI;
	}

	private static final class WhiteBgFilter implements Function<BufferedImage, BufferedImage> {

		@Override
		public BufferedImage apply(BufferedImage img) {

			int width = img.getWidth();
			int height = img.getHeight();

			BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = finalImage.createGraphics();
			g.drawImage(img, 0, 0, Color.WHITE, null);
			g.dispose();

			return finalImage;
		}
	}

	@Override
	public final boolean supportWebp() {
		return false;
	}
}
