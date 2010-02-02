/*
 *   $Id$
 *
 *   Copyright 2009 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.services.blitz.repo;

import static omero.rtypes.rlong;
import static omero.rtypes.rtime;
import static omero.rtypes.rstring;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import ome.services.util.Executor;
import ome.system.Principal;
import ome.system.ServiceFactory;
import omero.ApiUsageException;
import omero.ServerError;
import omero.ValidationException;
import omero.api.RawFileStorePrx;
import omero.api.RawPixelsStorePrx;
import omero.api.RenderingEnginePrx;
import omero.api.ThumbnailStorePrx;
import omero.grid.RepositoryPrx;
import omero.grid._RepositoryDisp;
import omero.model.Format;
import omero.model.FormatI;
import omero.model.OriginalFile;
import omero.model.OriginalFileI;
import omero.util.IceMapper;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.springframework.transaction.annotation.Transactional;

import Ice.Current;

/**
 * 
 * @since Beta4.1
 */
public class PublicRepositoryI extends _RepositoryDisp {

    private final static Log log = LogFactory.getLog(PublicRepositoryI.class);

    private final long id;

    private final File root;

    private final Executor executor;

    private final Principal principal;

    public PublicRepositoryI(File root, long repoObjectId, Executor executor,
            Principal principal) throws Exception {
        this.id = repoObjectId;
        this.executor = executor;
        this.principal = principal;

        if (root == null || !root.isDirectory()) {
            throw new ValidationException(null, null,
                    "Root directory must be a existing, readable directory.");
        }
        this.root = root.getAbsoluteFile();

    }

    public OriginalFile root(Current __current) throws ServerError {
        return new OriginalFileI(this.id, false); // SHOULD BE LOADED.
    }

    /**
     * Register an OriginalFile using its path
     * 
     * @param path
     *            Absolute path of the file to be registered.
     * @param __current
     *            ice context.
     * @return The OriginalFile with id set (unloaded)
     *
     */
    public OriginalFile register(String path, Format fmt, Current __current)
            throws ServerError {

        if (path == null || fmt == null
                || (fmt.getId() == null && fmt.getValue() == null)) {
            throw new ValidationException(null, null,
                    "path and fmt are required arguments");
        }

        File file = new File(path).getAbsoluteFile();
        OriginalFile omeroFile = new OriginalFileI();
        omeroFile = createOriginalFile(file);
        omeroFile.setFormat(fmt);

        IceMapper mapper = new IceMapper();
        final ome.model.core.OriginalFile omeFile = (ome.model.core.OriginalFile) mapper
                .reverse(omeroFile);
        Long id = (Long) executor.execute(principal, new Executor.SimpleWork(
                this, "register") {
            @Transactional(readOnly = false)
            public Object doWork(Session session, ServiceFactory sf) {
                return sf.getUpdateService().saveAndReturnObject(omeFile).getId();
            }
        });

        omeroFile.setId(rlong(id));
        omeroFile.unload();
        return omeroFile;

    }
    
    /**
     * Register an OriginalFile object
     * 
     * @param file
     *            OriginalFile object.
     * @param __current
     *            ice context.
     * @return The OriginalFile with id set (unloaded)
     *
     */
    public OriginalFile registerOriginalFile(OriginalFile file, Current __current)
            throws ServerError {

        if (file == null) {
            throw new ValidationException(null, null,
                    "file is required argument");
        }

        IceMapper mapper = new IceMapper();
        final ome.model.core.OriginalFile omeFile = (ome.model.core.OriginalFile) mapper
                .reverse(file);
        Long id = (Long) executor.execute(principal, new Executor.SimpleWork(
                this, "registerOriginalFile") {
            @Transactional(readOnly = false)
            public Object doWork(Session session, ServiceFactory sf) {
                return sf.getUpdateService().saveAndReturnObject(omeFile).getId();
            }
        });
        
        file.setId(rlong(id));
        file.unload();
        return file;
    }

    public void delete(String path, Current __current) throws ServerError {
        File file = checkPath(path);
        FileUtils.deleteQuietly(file);
    }

    @SuppressWarnings("unchecked")
    
    public List<OriginalFile> list(String path, Current __current) throws ServerError {
        File file = checkPath(path);
        List<File> files = Arrays.asList(file.listFiles());
        return filesToOriginalFiles(files);
    }

    public List<OriginalFile> listDirs(String path, Current __current)
            throws ServerError {
        File file = checkPath(path);
        List<File> files = Arrays.asList(file.listFiles((FileFilter)FileFilterUtils.directoryFileFilter()));
        return filesToOriginalFiles(files);
    }

    public List<OriginalFile> listFiles(String path, Current __current)
            throws ServerError {
        File file = checkPath(path);
        List<File> files = Arrays.asList(file.listFiles((FileFilter)FileFilterUtils.fileFileFilter()));
        return filesToOriginalFiles(files);
    }

    public OriginalFile load(String path, Current __current) throws ServerError {
        // TODO Auto-generated method stub
        return null;
    }

    public RawPixelsStorePrx pixels(String path, Current __current)
            throws ServerError {
        // TODO Auto-generated method stub
        return null;
    }

    public RawFileStorePrx read(String path, Current __current)
            throws ServerError {
        // TODO Auto-generated method stub
        return null;
    }

    public void rename(String path, Current __current) throws ServerError {
        // TODO Auto-generated method stub

    }

    public RenderingEnginePrx render(String path, Current __current)
            throws ServerError {
        // TODO Auto-generated method stub
        return null;
    }

    public ThumbnailStorePrx thumbs(String path, Current __current)
            throws ServerError {
        // TODO Auto-generated method stub
        return null;
    }

    public void transfer(String srcPath, RepositoryPrx target,
            String targetPath, Current __current) throws ServerError {
        // TODO Auto-generated method stub

    }

    public RawFileStorePrx write(String path, Current __current)
            throws ServerError {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Get a list of those files and directories at path that are already registered.
     * 
     * @param path
     *            A path on a repository.
     * @param __current
     *            ice context.
     * @return List of OriginalFile objects at path
     *
     */
    public List<OriginalFile> listKnown(String path, Current __current)
            throws ServerError {
        File file = checkPath(path);
        List<File> files = Arrays.asList(file.listFiles());
        return knownOriginalFiles(files);
    }

    /**
     * Get a list of directories at path that are already registered.
     * 
     * @param path
     *            A path on a repository.
     * @param __current
     *            ice context.
     * @return List of OriginalFile objects at path
     *
     */
    public List<OriginalFile> listKnownDirs(String path, Current __current)
            throws ServerError {
        File file = checkPath(path);
        List<File> files = Arrays.asList(file.listFiles((FileFilter)FileFilterUtils.directoryFileFilter()));
        return knownOriginalFiles(files);
    }

    /**
     * Get a list of files at path that are already registered.
     * 
     * @param path
     *            A path on a repository.
     * @param __current
     *            ice context.
     * @return List of OriginalFile objects at path
     *
     */
    public List<OriginalFile> listKnownFiles(String path, Current __current)
            throws ServerError {
        File file = checkPath(path);
        List<File> files = Arrays.asList(file.listFiles((FileFilter)FileFilterUtils.fileFileFilter()));
        return knownOriginalFiles(files);
    }

    public Format format(String path, Current __current) throws ServerError {
        return getFileFormat(path);
    }

    //
    // Utilities
    //

    private File checkPath(String path) throws ValidationException {

        if (path == null || path.length() == 0) {
            throw new ValidationException(null, null, "Path is empty");
        }

        boolean found = false;
        File file = new File(path).getAbsoluteFile();
        while (true) {
            if (file.equals(root)) {
                found = true;
                break;
            }
            file = file.getParentFile();
            if (file == null) {
                break;
            }
        }

        if (!found) {
            throw new ValidationException(null, null, path + " is not within "
                    + root.getAbsolutePath());
        }

        return new File(path).getAbsoluteFile();
    }
    
    /**
     * Get the Format for a file given its path
     * 
     * @param path
     *            A path on a repository.
     * @return A Format object
     *
     * TODO Return the correct Format object in place of a dummy one
     */
    private Format getFileFormat(String path) {
        Format format = new FormatI();
        format.setValue(rstring("DUMMY-FORMAT"));
        return format;
    }

    /**
     * Get OriginalFile objects corresponding to a collection of File objects.
     * 
     * @param files
     *            A collection of File objects.
     * @return A list of new OriginalFile objects
     *
     */
    private List<OriginalFile> filesToOriginalFiles(Collection<File> files) {
        List rv = new ArrayList<OriginalFile>();
        for (File f : files) {
            rv.add(createOriginalFile(f));
        }
        return rv;
    }
    
    /**
     * Get registered OriginalFile objects corresponding to a collection of File objects.
     * 
     * @param files
     *            A collection of File objects.
     * @return A list of registered OriginalFile objects. 
     *
     */
    private List<OriginalFile> knownOriginalFiles(Collection<File> files)  {
        List rv = new ArrayList<OriginalFile>();
        for (File f : files) {
            List<OriginalFile> fileList = getOriginalFiles(f.getAbsolutePath());
            rv.addAll(fileList);
        }
        return rv;
    }
    

    /**
     * Create an OriginalFile object corresponding to a File object.
     * 
     * @param f
     *            A File object.
     * @return An OriginalFile object
     *
     * TODO populate more attribute fields than the few set here.
     */
    private OriginalFile createOriginalFile(File f) {
        OriginalFile file = new OriginalFileI();
        file.setPath(rstring(f.getAbsolutePath()));
        file.setMtime(rtime(f.lastModified()));
        file.setSize(rlong(f.length()));
        file.setSha1(rstring("UNKNOWN"));
        // What more do I need to set here, more times, details?
        
        // This needs to be unique - see ticket #1753
        file.setName(rstring(f.getAbsolutePath()));
        
        file.setFormat(getFileFormat(f.getAbsolutePath()));
        
        return file;
    }
    
    /**
     * Get a list of OriginalFiles with path corresponding to the paramater path.
     * 
     * @param path
     *            A path to a file.
     * @return List of OriginalFile objects, empty if the query returned no values.
     *
     * TODO Weak at present, returns all matched files based on path.
     *      There should be further checking for uniqueness
     */
    private List<OriginalFile> getOriginalFiles(String path)  {
        
        List rv = new ArrayList<OriginalFile>();
        final String queryString = "from OriginalFile as o where o.path = '"
                    + path + "'";
        List<ome.model.core.OriginalFile> fileList = (List<ome.model.core.OriginalFile>) executor
                .execute(principal, new Executor.SimpleWork(this, "getOriginalFiles") {

                    @Transactional(readOnly = true)
                    public Object doWork(Session session, ServiceFactory sf) {
                        return sf.getQueryService().findAllByQuery(queryString,
                                null);
                    }
                });
            
        if (fileList == null) {
            return rv;
        }
        if (fileList.size() == 0) {
            return rv;
        }
        IceMapper mapper = new IceMapper();
        rv = (List<OriginalFile>) mapper.map(fileList);

        return rv;
    }



}
