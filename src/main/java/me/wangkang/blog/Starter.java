package me.wangkang.blog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Starter {
	private static final String[] jettyJars = { "libs/jetty-server.jar", "libs/jetty-web-app.jar",
			"libs/jetty-continuation.jar", "libs/jetty-http.jar", "libs/jetty-io.jar", "libs/jetty-security.jar",
			"libs/jetty-servlet.jar", "libs/jetty-util.jar", "libs/jetty-xml.jar", "libs/javax-servlet-api.jar",
			"libs/jettylaunch.jar" };

	public static void main(String[] args) throws Exception {
		ProtectionDomain protectionDomain = Starter.class.getProtectionDomain();
		URL warUrl = protectionDomain.getCodeSource().getLocation();
		List<?> jarUrls = extractJettyJarsFromWar(warUrl.getPath());
		ClassLoader urlClassLoader = new URLClassLoader((URL[]) jarUrls.toArray(new URL[jarUrls.size()]));
		Thread.currentThread().setContextClassLoader(urlClassLoader);
		Class<?> jettyUtil = urlClassLoader.loadClass("JettyLauncher");
		Method mainMethod = jettyUtil.getMethod("start", new Class[] { URL.class });
		mainMethod.invoke(null, new Object[] { warUrl });
	}

	private static List<URL> extractJettyJarsFromWar(String warPath) throws IOException {
		JarFile jarFile = new JarFile(warPath);
		try {
			List<URL> jarUrls = new ArrayList<URL>();
			InputStream inStream = null;
			try {
				File tmpFile = null;
				for (String entryPath : jettyJars) {
					try {
						tmpFile = File.createTempFile(entryPath.replaceAll("/", "_"), "blog");
					} catch (IOException e) {
						String tmpdir = System.getProperty("java.io.tmpdir");
						throw new IOException("Failed to extract " + entryPath + " to " + tmpdir, e);
					}
					JarEntry jarEntry = jarFile.getJarEntry(entryPath);
					inStream = jarFile.getInputStream(jarEntry);

					OutputStream outStream = new FileOutputStream(tmpFile);
					try {
						byte[] buffer = new byte[8192];
						int readLength;
						while ((readLength = inStream.read(buffer)) > 0) {
							outStream.write(buffer, 0, readLength);
						}
					} catch (Exception exc) {
						exc.printStackTrace();
					} finally {
						outStream.close();
					}
					tmpFile.deleteOnExit();
					jarUrls.add(tmpFile.toURI().toURL());
				}
			} catch (Exception exc) {
				exc.printStackTrace();
			} finally {
			}
			return jarUrls;
		} finally {
			if (jarFile != null) {
				jarFile.close();
			}
		}
	}
}
