/*
Copyright (c) 2008-2024, Damon Hart-Davis
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the
    distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.hd.d.edh;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**File-handling utilities.
 */
public final class FileUtils
    {
	/**Prevent creation of an instance. */
    private FileUtils() { }

    /**Prefix used on temporary files, eg while doing atomic replacements.
	 * This used to be in GlobalParams but we may even need it
	 * while loading GlobalParams.
	 */
	private static final String F_tmpPrefix = ".tmp.";

	/**Private lock for replacePublishedFile().
	 * We use a read/write lock to improve available concurrency.
	 * <p>
	 * TODO: We could extend this to a lock per distinct directory or filesystem.
	 */
	private static final ReentrantReadWriteLock rPF_rwlock = new ReentrantReadWriteLock();

	/**Private moderate pseudo-random-number source for replacePublishedFile(); not null. */
	private static final Random rnd = new Random();

	/**Replaces an existing published file with a new one (see 3-arg version).
	 * Is verbose when it replaces the file.
	 */
	public static boolean replacePublishedFile(final String name, final byte data[])
	    throws IOException
	    { return(FileUtils.replacePublishedFile(name, data, false)); }

	/**Replaces an existing published file with a new one.
     * This replaces (atomically if possible) the existing file (if any)
     * of the given name, ensuring the correct permissions for
     * a file to be published with a Web server (ie basically
     * global read permissions), provided the following
     * conditions are met:
     * <p>
     * <ul>
     * <li>The filename extension is acceptable (not checked yet).
     * <li>The data array is non-null and not zero-length.
     * <li>The content of the data array is different to the file.
     * <li>All the required permissions are available.
     * </ul>
     * <p>
     * If the file is successfully replaced, true is returned.
     * <p>
     * If the file does not need replacing, false is returned.
     * <p>
     * If an error occurs, eg in the input data or during file
     * operations, an IOException is thrown.
     * <p>
     * This routine enforces locking so that only one such
     * operation may be performed at any one time.  This does
     * not avoid the possibility of externally-generated races.
     * <p>
     * The final file, once replaced, will be globally readable,
     * and writable by us.
     * <p>
     * (If the final component of the file starts with ".",
     * then the file will be accessible only by us.)
     *
     * @param quiet     if true then only error messages will be output
     */
    public static boolean replacePublishedFile(final String name, final byte data[],
                                               final boolean quiet)
        throws IOException
        {
        if((name == null) || (name.length() == 0))
            { throw new IOException("inappropriate file name"); }
        if((data == null) || (data.length == 0))
            { throw new IOException("inappropriate file content"); }

        final File extant = new File(name);

        // Lock the critical external bits against read and write updates.
        FileUtils.rPF_rwlock.writeLock().lock();
        try
            {
            // Use a temporary file in the same directory (and thus the same filesystem)
            // to avoid unexpectedly truncating the file when copying/moving it.
            File tempFile;
            for( ; ; )
                {
                tempFile = new File(extant.getParent(),
                    F_tmpPrefix +
                    Long.toString((rnd.nextLong() >>> 1),
                        Character.MAX_RADIX) /* +
                    "." +
                    extant.getName() */ ); // Avoid making very long names...
                if(tempFile.exists())
                    {
                    System.err.println("WARNING: FileTools.replacePublishedFile(): "+
                        "temporary file " + tempFile.getPath() +
                        " exists, looping...");
                    continue;
                    }
                break;
                }

            // Get extant file's length.
            final long oldLength = extant.length();
            // Should we overwrite it?
            boolean overwrite = (oldLength < 1); // Missing or zero length.

            // If length has changed, we should overwrite.
            if(data.length != oldLength) { overwrite = true; }

            // Now, if we haven't already decided to overwrite the file,
            // check the content.
            if(!overwrite)
                {
                try
                    {
                    final InputStream is = new BufferedInputStream(
                        new FileInputStream(extant));
                    try (is)
                        {
                        final int l = data.length;
                        for(int i = 0; i < l; ++i)
                            {
                            if(data[i] != is.read())
                                { overwrite = true; break; }
                            }
                        }
                    }
                catch(final FileNotFoundException e) { overwrite = true; }
                }

            // OK, we don't want to overwrite, so return.
            if(!overwrite) { return(false); }


            // OVERWRITE OLD FILE WITH NEW...

            try {
                // Write new temp file...
                // (Allow any IOException to terminate the function.)
                OutputStream os = new FileOutputStream(tempFile);
                os.write(data);
                // os.flush(); // Possibly avoid unnecessary premature disc flush here.
                os.close();
                os = null; // Help GC.
                if(tempFile.length() != data.length)
                    { new IOException("temp file not written correctly"); }

                final boolean globalRead = !extant.getName().startsWith(".");

                // Ensure that the temp file has the correct read permissions.
                tempFile.setReadable(true, !globalRead);
                tempFile.setWritable(true, true);

                // Warn if target does not have write perms, and try to add them.
                // This should allow us to replace it with the new file.
                final boolean alreadyExists = extant.exists();
                if(alreadyExists && !extant.canWrite())
                    {
                    System.err.println("FileTools.replacePublishedFile(): "+
                        "WARNING: " + name + " not writable.");
                    extant.setWritable(true, true);
                    if(!extant.canWrite())
                        {
                        throw new IOException("can't make target writable");
                        }
                    }

                // (Atomically) move tempFile to extant file.
                // Note that renameTo() may not be atomic
                // and we may have to remove the target file first.
                if(!tempFile.renameTo(extant))
                    {
                    // If the target already exists,
                    // then be prepared to explicitly delete it.
                    if(!alreadyExists || !extant.delete() || !tempFile.renameTo(extant))
                        { throw new IOException("renameTo/update of "+name+" failed"); }
                    if(!quiet) { System.err.println("[WARNING: atomic replacement not possible for: " + name + ": used explicit delete.]"); }
                    }

                if(extant.length() != data.length)
                    { new IOException("update of "+name+" failed"); }
                extant.setReadable(true, !globalRead);
                extant.setWritable(true, true);
                if(!quiet) { System.err.println("["+(alreadyExists?"Updated":"Created")+" " + name + "]"); }
                return(true); // All seems OK.
                }
            finally // Tidy up...
                {
                tempFile.delete(); // Remove the temp file.
//                Thread.yield(); // That was probably expensive; give up the CPU...
                }
            }
        finally { FileUtils.rPF_rwlock.writeLock().unlock(); }

        // Can't get here...
        }

	/**Given a file, serialises an object to it.
	 * This atomically replaces the target file if possible.
	 *
	 * @param gzipped  if true, the file is written GZIP-compressed
	 *     to (usually) save significant space
	 * @param quiet  if true, only outputs errors
	 *
	 * @throws IOException  if something bad happens
	 */
	public static void serialiseToFile(final Object o,
	                                   final File filename, final boolean gzipped,
	                                   final boolean quiet)
	    throws IOException
	    {
	    ObjectOutputStream oos = null;
	    try {
	        // Serialise to a byte[].
	        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        // We may gzip the stream to remove some of the redundancy.
	        final java.util.zip.GZIPOutputStream gos = gzipped ?
	            new java.util.zip.GZIPOutputStream(baos) : null;
	        oos = new ObjectOutputStream(gzipped ?
	    		new BufferedOutputStream(gos) : baos);
	        oos.writeObject(o);
	        oos.flush();
	        if(gos != null) { gos.finish(); }
	        final byte data[] = baos.toByteArray();

	        // Now save to file using pure Java APIs.
	        // Write-locked against other file replacements.
	        replacePublishedFile(filename.getPath(), data, quiet);
	        }
	    finally
	        {
	        if(oos != null) { oos.close(); } // Free up OS resources.
	        }
	    }

	/**Given a file, deserialises an object from it.
	 * This buffers the input for efficiency.
	 *
	 * @param gzipped  if true, the file is assumed to be in GZIP
	 *     format and the stream is decompressed on the fly
	 * @throws IOException  if something bad happens
	 */
	public static Object deserialiseFromFile(final File filename, final boolean gzipped)
	    throws IOException
	    {
	    ObjectInputStream ois = null;

	    // Lock out concurrent published-file updates.
	    rPF_rwlock.readLock().lock();
	    try {
	        // Need to reload from disc.
	        InputStream is = new BufferedInputStream(new FileInputStream(
	            filename), 8192);
	        if(gzipped) { is = new java.util.zip.GZIPInputStream(is, 8192); }
	        ois = new ObjectInputStream(is);
	        return(ois.readObject());
	        }
	    catch(final ClassNotFoundException e)
	        { throw new IOException("ClassNotFoundException deserialising file ``"+filename+"'': " + e.getMessage()); }
	    catch(final Exception e)
	        {
	        final IOException err = new IOException("unexpected exception deserialising file ``"+filename+"'': " + e.getMessage());
	        err.initCause(e);
	        throw err;
	        }
	    finally
	        {
	        // Potentially allow updates again.
	        rPF_rwlock.readLock().unlock();

	        // Ensure that we release OS file-handling resources.
	        if(ois != null) { ois.close(); }
	        }
	    }
    }
