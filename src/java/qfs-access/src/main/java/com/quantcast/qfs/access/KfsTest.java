/**
 * $Id$
 *
 * Created 2007/08/25
 *
 * Copyright 2008-2012 Quantcast Corp.
 * Copyright 2007 Kosmix Corp.
 *
 * This file is part of Kosmos File System (KFS).
 *
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * \brief A Java unit test program to access KFSAccess APIs.
 */


package com.quantcast.qfs.access;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.nio.ByteBuffer;

public class KfsTest
{
    public static void main(String args[]) {
        if (args.length < 1) {
            System.out.println("Usage: KfsTest <meta server> <port>");
            System.exit(1);
        }
        try {
            int port = Integer.parseInt(args[1].trim());
            KfsAccess kfsAccess = new KfsAccess(args[0], port);

            String basedir = new String("jtest");
            final String euidp = System.getProperty("kfs.euid");
            final String egidp = System.getProperty("kfs.egid");
            final long   euid  = (euidp != null && euidp.length() > 0) ?
                Long.decode(euidp) : KfsFileAttr.KFS_USER_NONE;
            final long   egid  = (egidp != null && egidp.length() > 0) ?
                Long.decode(egidp) : KfsFileAttr.KFS_GROUP_NONE;
            if (euid != KfsFileAttr.KFS_USER_NONE ||
                    egid != KfsFileAttr.KFS_GROUP_NONE) {
                kfsAccess.kfs_retToIOException(
                    kfsAccess.kfs_setEUserAndEGroup(euid, egid, null));
            }

            KfsDelegation dtoken = null;
            try {
                final boolean       allowDelegationFlag   = true;
                final long          delegationValidForSec = 24 * 60 * 60;
                final KfsDelegation result                =
                    kfsAccess.kfs_createDelegationToken(
                        allowDelegationFlag, delegationValidForSec);
                System.out.println("create delegation token: ");
                System.out.println("delegation:  " + result.delegationAllowedFlag);
                System.out.println("issued:      " + result.issuedTime);
                System.out.println("token valid: " + result.tokenValidForSec);
                System.out.println("valid:       " + result.delegationValidForSec);
                System.out.println("token:       " + result.token);
                System.out.println("key:         " + result.key);
                dtoken = result;
            } catch (IOException ex) {
                String msg = ex.getMessage();
                if (msg == null) {
                    msg = "null";
                }
                System.out.println("create delegation token error: " + msg);
            }

            if (dtoken != null) {
                try {
                    kfsAccess.kfs_renewDelegationToken(dtoken);
                    System.out.println("renew delegation token: ");
                    System.out.println("delegation:  " + dtoken.delegationAllowedFlag);
                    System.out.println("issued:      " + dtoken.issuedTime);
                    System.out.println("token valid: " + dtoken.tokenValidForSec);
                    System.out.println("valid:       " + dtoken.delegationValidForSec);
                    System.out.println("token:       " + dtoken.token);
                    System.out.println("key:         " + dtoken.key);
                } catch (IOException ex) {
                    String msg = ex.getMessage();
                    if (msg == null) {
                        msg = "null";
                    }
                    System.out.println("renew delegation token error: " + msg);
                    throw ex;
                }
                try {
                    KfsDelegation ctoken = new KfsDelegation();
                    ctoken.token = dtoken.token;
                    ctoken.key   = dtoken.key;
                    kfsAccess.kfs_cancelDelegationToken(ctoken);
                } catch (IOException ex) {
                    String msg = ex.getMessage();
                    if (msg == null) {
                        msg = "null";
                    }
                    System.out.println("cancel delegation token error: " + msg);
                    throw ex;
                }
                try {
                    kfsAccess.kfs_renewDelegationToken(dtoken);
                } catch (IOException ex) {
                    String msg = ex.getMessage();
                    if (msg == null) {
                        msg = "null";
                    }
                    System.out.println("renew canceled delegation token error: " + msg);
                    dtoken = null;
                }
                if (dtoken != null) {
                    System.out.println("Token renew after cancellation succeeded");
                    System.exit(1);
                }
            }

            if (! kfsAccess.kfs_exists(basedir)) {
                if (kfsAccess.kfs_mkdirs(basedir) != 0) {
                    System.out.println("Unable to mkdir");
                    System.exit(1);
                }
            }

            if (!kfsAccess.kfs_isDirectory(basedir)) {
                System.out.println("KFS doesn't think " + basedir + " is a dir!");
                System.exit(1);

            }
            String path = new String(basedir + "/foo.1");
            KfsOutputChannel outputChannel;
            if ((outputChannel = kfsAccess.kfs_create(path)) == null) {
                System.out.println("Unable to call create");
                System.exit(1);
            }

            long mTime = kfsAccess.kfs_getModificationTime(path);
            Date d = new Date(mTime);
            System.out.println("Modification time for: " + path + " is: " + d.toString());

            // test readdir and readdirplus
            String [] entries;
            if ((entries = kfsAccess.kfs_readdir(basedir)) == null) {
                System.out.println("Readdir failed");
                System.exit(1);
            }

            System.out.println("Readdir returned: ");
            for (int i = 0; i < entries.length; i++) {
                System.out.println(entries[i]);
            }

            final String absent = basedir + "/must not exist";
            if ((entries = kfsAccess.kfs_readdir(absent)) != null) {
                System.out.println("kfs_readdir: " + absent +
                    " non null, size: " + entries.length);
                System.exit(1);
            }

            // write something
            int numBytes = 2048;
            char [] dataBuf = new char[numBytes];

            generateData(dataBuf, numBytes);

            String s = new String(dataBuf);
            byte[] buf = s.getBytes();

            ByteBuffer b = ByteBuffer.wrap(buf, 0, buf.length);
            int res = outputChannel.write(b);
            if (res != buf.length) {
                System.out.println("Was able to write only: " + res);
            }

            // flush out the changes
            outputChannel.sync();

            outputChannel.close();

            KfsFileAttr[] fattr;
            if ((fattr = kfsAccess.kfs_readdirplus(basedir)) == null) {
                System.out.println("kfs_readdirplus failed");
                System.exit(1);
            }

            System.out.println("kfs_readdirplus returned: ");
            for (int i = 0; i < fattr.length; i++) {
                System.out.println(attrToString(fattr[i], "\n"));
            }

            if ((fattr = kfsAccess.kfs_readdirplus(absent)) != null) {
                System.out.println("kfs_readdirplus: " + fattr +
                    ": non null, size: " + fattr.length);
                System.exit(1);
            }

            System.out.println("Trying to lookup blocks for file: " + path);

            String [][] locs;
            if ((locs = kfsAccess.kfs_getDataLocation(path, 10, 512)) == null) {
                System.out.println("kfs_getDataLocation failed");
                System.exit(1);
            }

            System.out.println("Block Locations:");
            for (int i = 0; i < locs.length; i++) {
                System.out.print("chunk " + i + " : ");
                for (int j = 0; j < locs[i].length; j++) {
                    System.out.print(locs[i][j] + " ");
                }
                System.out.println();
            }

            if ((locs = kfsAccess.kfs_getBlocksLocation(path, 10, 512)) == null) {
                System.out.println("kfs_getBlocksLocation failed");
                System.exit(1);
            }
            if (locs.length < 1 || locs[0].length != 1) {
                System.out.println("kfs_getBlocksLocation invalid first slot length");
                System.exit(1);
            }
            final long blockSize = Long.parseLong(locs[0][0], 16);
            if (blockSize < 0) {
                kfsAccess.kfs_retToIOException((int)blockSize, path);
            }
            System.out.println("block size: " + blockSize);
            for (int i = 1; i < locs.length; i++) {
                System.out.print("chunk " + (i-1) + " : ");
                for (int j = 0; j < locs[i].length; j++) {
                    System.out.print(locs[i][j] + " ");
                }
                System.out.println();
            }

            long sz = kfsAccess.kfs_filesize(path);

            if (sz != buf.length) {
                System.out.println("System thinks the file's size is: " + sz);
            }

            KfsFileAttr attr = new KfsFileAttr();
            final int ret = kfsAccess.kfs_stat(path, attr);
            if (ret != 0) {
                System.out.println(path + ": stat failed: " + ret);
                System.exit(1);
            }
            System.out.println("stat: \n" + attrToString(attr, "\n"));

            // rename the file
            String npath = new String(basedir + "/foo.2");
            kfsAccess.kfs_rename(path, npath);

            if (kfsAccess.kfs_exists(path)) {
                System.out.println(path + " still exists after rename!");
                System.exit(1);
            }

            KfsOutputChannel outputChannel1 = kfsAccess.kfs_create(path);

            if (outputChannel1 != null) {
                outputChannel1.close();
            }

            if (!kfsAccess.kfs_exists(path)) {
                System.out.println(path + " doesn't exist");
                System.exit(1);
            }

            // try to rename and don't allow overwrite
            if (kfsAccess.kfs_rename(npath, path, false) == 0) {
                System.out.println("Rename with overwrite disabled succeeded!");
                System.exit(1);
            }

            kfsAccess.kfs_remove(path);

            if (!kfsAccess.kfs_isFile(npath)) {
                System.out.println(npath + " is not a normal file!");
                System.exit(1);
            }

            KfsInputChannel inputChannel = kfsAccess.kfs_open(npath);
            if (inputChannel == null) {
                System.out.println("open on " + npath + "failed!");
                System.exit(1);
            }

            // read some bytes
            buf = new byte[128];
            res = inputChannel.read(ByteBuffer.wrap(buf, 0, 128));

            s = new String(buf);
            for (int i = 0; i < 128; i++) {
                if (dataBuf[i] != s.charAt(i)) {
                    System.out.println("Data mismatch at char: " + i);
                }
            }

            // seek to offset 40
            inputChannel.seek(40);

            sz = inputChannel.tell();
            if (sz != 40) {
                System.out.println("After seek, we are at: " + sz);
            }

            inputChannel.close();

            // remove the file
            kfsAccess.kfs_remove(npath);

            testDirs(kfsAccess, basedir + "/");

            // Test recursive remove.
            final String rtest    = basedir + "/rtest";
            final String testPath = rtest + "/a/b/../../c/../d";
            kfsAccess.kfs_retToIOException(kfsAccess.kfs_mkdirs(testPath));
            if (! kfsAccess.kfs_exists(testPath)) {
                System.out.println(testPath + " doesn't exist");
                System.exit(1);
            }
            kfsAccess.kfs_create(testPath + "/test_file").close();
            kfsAccess.kfs_retToIOException(kfsAccess.kfs_rmdirs(rtest));
            if (kfsAccess.kfs_exists(rtest)) {
                System.out.println(rtest + " exist");
                System.exit(1);
            }

            // remove the dir
            if (kfsAccess.kfs_rmdir(basedir) < 0) {
                System.out.println("unable to remove: " + basedir);
                System.exit(1);
            }

            // test new create methods
            testCreateAPI(kfsAccess, basedir);

            // test read when read-ahead is disabled
            testDisableReadAhead(kfsAccess, basedir);

            final Iterator<Map.Entry<String, String> > it =
                kfsAccess.kfs_getStats().entrySet().iterator();
            System.out.println("Clients stats:");
            while (it.hasNext()) {
                final Map.Entry<String, String> entry = it.next();
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }

            System.out.println("All done...Test passed!");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Unable to setup KfsAccess");
            System.exit(1);
        }
    }

    private static Random randGen = new Random(100);

    private static void generateData(char buf[], int numBytes)
    {
        int i;
        // String nameBuf = new String("sriram");

        for (i = 0; i < numBytes; i++) {
            buf[i] = (char) ('a' + (randGen.nextInt(26)));
            // buf[i] = nameBuf.charAt((i + 6) % 6);
        }
    }

    private static String attrToString(KfsFileAttr attr, String delim)
    {
        return
        "filename: "           + attr.filename + delim +
        "isDirectory: "        + attr.isDirectory + delim +
        "filesize: "           + attr.filesize + delim +
        "modificationTime: "   + attr.modificationTime + delim +
        "replication: "        + attr.replication + delim +
        "striperType: "        + attr.striperType + delim +
        "numStripes: "         + attr.numStripes + delim +
        "numRecoveryStripes: " + attr.numRecoveryStripes + delim +
        "stripeSize: "         + attr.stripeSize + delim +
        "owner: "              + attr.owner + delim +
        "group: "              + attr.group + delim +
        "mode: "               + attr.mode + delim +
        "fileId: "             + attr.fileId + delim +
        "ownerName: "          + attr.ownerName + delim +
        "groupName: "          + attr.groupName;
    }

    private static void testDirs(KfsAccess fs, String root) throws IOException
    {
        final String oldDir = root + "old";
        final String newDir = root + "new";
        for(int i = 0; i < 4; i++) {
            fs.kfs_retToIOException(fs.kfs_mkdirs(oldDir));
            System.out.println("Pass " + i + ": " +
                oldDir + " exists " + fs.kfs_exists(oldDir));
            if (fs.kfs_exists(newDir)) {
                final int ret = delete(fs, newDir);
                System.out.println("Pass " + i +
                    ": delete of " + newDir + " " + ret);
                fs.kfs_retToIOException(ret);
            }
            final int ret = rename(fs, oldDir, newDir);
            System.out.println("Pass " + i +
                ": rename to " + newDir + " "  + ret);
            fs.kfs_retToIOException(ret);
        }
        final int ret = delete(fs, newDir);
        fs.kfs_retToIOException(ret);
    }

    private static int rename(KfsAccess kfsAccess, String source, String dest)
            throws IOException
    {
	// KFS rename does not have mv semantics.
	// To move /a/b under /c/, you must ask for "rename /a/b /c/b"
	String renameTarget;
	if (kfsAccess.kfs_isDirectory(dest)) {
	    String sourceBasename = (new File(source)).getName();
	    if (dest.endsWith("/")) {
		renameTarget = dest + sourceBasename;
	    } else {
		renameTarget = dest + "/" + sourceBasename;
	    }
	} else {
	    renameTarget = dest;
	}
	return kfsAccess.kfs_rename(source, renameTarget);
    }

    // recursively delete the directory and its contents
    private static int delete(KfsAccess kfsAccess, String path)
            throws IOException {
        if (kfsAccess.kfs_isFile(path)) {
            return kfsAccess.kfs_remove(path);
        }
        return kfsAccess.kfs_rmdirs(path);
    }
 
    private static void testCreateAPI(KfsAccess kfsAccess, String baseDir)
            throws IOException {
        String filePath1 = baseDir + "/sample_file.1";
        String createParams = "1,6,3,1048576,2,15,15";
        KfsOutputChannel outputChannel = kfsAccess.kfs_create_ex(filePath1,
                true, createParams);
        if (outputChannel == null) {
            System.out.println("Unable to call create");
            System.exit(1);
        }
        verifyFileAttr(kfsAccess, outputChannel, filePath1);

        String filePath2 = baseDir + "/sample_file.2";
        outputChannel = kfsAccess.kfs_create_ex(filePath2, 1, true, -1, -1,
                6, 3, 1048576, 2, false, 0666, 15, 15);
        if (outputChannel == null) {
            System.out.println("Unable to call create");
            System.exit(1);
        }
        verifyFileAttr(kfsAccess, outputChannel, filePath2);

        delete(kfsAccess, filePath1);
        delete(kfsAccess, filePath2);
    }

    private static void verifyFileAttr(KfsAccess kfsAccess,
            KfsOutputChannel outputChannel, String filePath)
             throws IOException {
        int numBytes = 1048576;
        char[] dataBuf = new char[numBytes];
        generateData(dataBuf, numBytes);
        String s = new String(dataBuf);
        byte[] buf = s.getBytes();
        ByteBuffer b = ByteBuffer.wrap(buf, 0, buf.length);
        int res = outputChannel.write(b);
        if (res != buf.length) {
            System.out.println("Was able to write only: " + res);
            System.exit(1);
        }
        outputChannel.sync();
        outputChannel.close();
        KfsFileAttr attr = new KfsFileAttr();
        final int ret = kfsAccess.kfs_stat(filePath, attr);
        if (ret != 0) {
            System.out.println(filePath + ": stat failed: " + ret);
            System.exit(1);
        }
        if (numBytes != attr.filesize || attr.replication != 1 ||
                attr.striperType != 2 || attr.numStripes != 6 ||
                attr.numRecoveryStripes != 3 || attr.stripeSize != 1048576) {
            System.out.println("File attributes don't match.");
            System.exit(1);
        }
    }

    private static void testDisableReadAhead(KfsAccess kfsAccess, String baseDir)
            throws IOException {
        String filePath = baseDir + "/sample_file.1";
        String createParams = "S";
        KfsOutputChannel outputChannel = kfsAccess.kfs_create_ex(filePath,
                true, createParams);
        int numBytes = 1048576;
        char[] dataBuf = new char[numBytes];
        generateData(dataBuf, numBytes);
        String s = new String(dataBuf);
        byte[] buf = s.getBytes();
        ByteBuffer b = ByteBuffer.wrap(buf, 0, buf.length);
        int res = outputChannel.write(b);
        if (res != buf.length) {
            System.out.println("Was able to write only: " + res);
            System.exit(1);
        }
        if (outputChannel == null) {
            System.out.println("Unable to call create");
            System.exit(1);
        }
        outputChannel.sync();
        outputChannel.close();

        KfsInputChannel inputChannel = kfsAccess.kfs_open(filePath);
        if (inputChannel == null) {
            System.out.println("open on " + filePath + "failed!");
            System.exit(1);
        }

        inputChannel.setReadAheadSize(0);

        byte[] dstBuf = new byte[128];
        res = inputChannel.read(ByteBuffer.wrap(dstBuf, 0, 128));
        s = new String(dstBuf);
        for (int i = 0; i < 128; i++) {
            if (dataBuf[i] != s.charAt(i)) {
                System.out.println("Data mismatch at char: " + i);
                System.exit(1);
            }
        }

        inputChannel.seek(512);
        long pos = inputChannel.tell();
        if (pos != 512) {
            System.out.println("Couldn't seek to byte 512. Pos: " + pos);
            System.exit(1);
        }

        res = inputChannel.read(ByteBuffer.wrap(dstBuf, 0, 128));
        s = new String(dstBuf);
        for (int i = 0; i < 128; i++) {
            if (dataBuf[512+i] != s.charAt(i)) {
                System.out.println("Data mismatch at char " + i +
                                   " after seeking to byte 512");
                System.exit(1);
            }
        }

        // seek to the beginning, enable read-ahead and make a small read
        inputChannel.seek(0);
        pos = inputChannel.tell();
        if (pos != 0) {
            System.out.println("Couldn't seek to the beginning. Pos: " + pos);
            System.exit(1);
        }

        inputChannel.setReadAheadSize(1048576);

        res = inputChannel.read(ByteBuffer.wrap(dstBuf, 0, 128));
        s = new String(dstBuf);
        for (int i = 0; i < 128; i++) {
            if (dataBuf[i] != s.charAt(i)) {
                System.out.println("Data mismatch at char " + i +
                                   " after seeking to the beginning");
                System.exit(1);
            }
        }
        inputChannel.close();
    }
}
