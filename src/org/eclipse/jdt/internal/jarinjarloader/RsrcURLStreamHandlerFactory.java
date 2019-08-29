//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.eclipse.jdt.internal.jarinjarloader;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import org.eclipse.jdt.internal.jarinjarloader.RsrcURLStreamHandler;

public class RsrcURLStreamHandlerFactory implements URLStreamHandlerFactory {
    private ClassLoader classLoader;
    private URLStreamHandlerFactory chainFac;

    public RsrcURLStreamHandlerFactory(ClassLoader cl) {
        this.classLoader = cl;
    }

    public URLStreamHandler createURLStreamHandler(String protocol) {
        return (URLStreamHandler)("rsrc".equals(protocol)?new RsrcURLStreamHandler(this.classLoader):(this.chainFac != null?this.chainFac.createURLStreamHandler(protocol):null));
    }

    public void setURLStreamHandlerFactory(URLStreamHandlerFactory fac) {
        this.chainFac = fac;
    }
}
