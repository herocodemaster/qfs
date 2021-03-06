Metaserver
==========
The metaserver is responsible for storing all global QFS file system
information, tracking chunk locations, and coordinating chunk
replication/recovery. This section will discuss basic metaserver
administration. Configuration

The metaserver configuration is normally stored in a file called
`MetaServer.prp`. The [[Deployment Guide]] includes several minimal sample
configurations. For the complete set of configuration parameters see the
[[Configuration Reference]].

Running
-------
`metaserver /path/to/MetaServer.prp`

To initialize file system by creating initial empty file system's checkpoint and
log segment -c command line option can be used:

`metaserver -c /path/to/MetaServer.prp`

-c option should **not** be used when running meta server with existing QFS file
system, in order to prevent data loss in the case when meta server starts
with no "latest" checkpoint file.

Checkpoint and Transaction Log Pruning
--------------------------------------
The directories that store metaserver checkpoints (*metaServer.cpDir*) and
transaction logs (*metaServer.logDir*) must be pruned regularly; otherwise they
will fill up and run out of space. The pruning interval should be based on the
configured checkpoint frequency (*metaServer.checkpoint.interval*).

To prune the checkpoints directory:

`qfs_checkpoint_prune.py /path/to/metaServer.cpDir`

and for the transaction logs do:

`qfs_log_prune.py /path/to/metaServer.logDir`

Creating Backups
----------------
From time to time the *metaServer.cpDir* and *metaServer.logDir* should be
backed up, as they can be used to restore a file system which has had a
catastrophic failure. A backup consists of:

- The **latest** checkpoint file in *metaServer.cpDir*
- **ALL** of the transaction logs in *metaServer.logDir*

The simplest way to back up a file system image is to use `tar` to archive these
files.

Given the following configuration:

    metaServer.cpDir = /home/qfs0/state/checkpoint
    metaServer.logDir = /home/qfs0/state/transactions

A possible solution would be to periodically do the following:

    tar --exclude '*.tmp.??????' -czf /foo/bar/qfs0-backup-`date +%d-%H.tar.gz` checkpoint transactions -C /home/qfs0/state`

**Note**: this simple script includes all checkpoint files, which is
inefficient; only the latest checkpoint file is required for the backup.

Using `date +%d-%H` in the file name has the advantage of automatic backup
rotation, as it will roll over once a month. One backup file will be kept for
each hour of the day, every day of the month (e.g. 24 files for September 26th,
24 files for September 27th, etc.). Backups should be archived to a safe place,
that is, **not** their own QFS file system. A different QFS file system
designated for backups with a high replication count might be advisable.

Restoring Backups
-----------------
To restore a backup, it need only be extracted to the appropriate
*metaServer.cpDir* and *metaServer.logDir* directories of a fresh metaserver
head node.

Using the configuration from the previous example:

    cd /home/qfs0/state && tar -xzf /foo/bar/qfs0-backup-31-23.tar.gz

Once the metaserver is started, it will read the latest checkpoint into memory
and replay any transaction logs. Files that were allocated since the backup
will no longer exist and chunks associated with these files will be deleted.
Files that have been deleted since the backup, however, will show up as lost (as
their chunks will have been deleted) but the restored file system image will
still reference them. After a restore, you should run a file system integrity
check and then delete the lost files it identifies. Lastly, any other file
modifications since the backup will be lost.

**Note:** The location of the *metaServer.cpDir* and *metaServer.logDir* should
not change.

File System Integrity (`qfsfsck`)
-------------------------------
The `qfsfsck` tool can be employed in three ways:

- Verify the integrity of a running file system by identifying lost files and/or
  files with chunk placement/replication problems.
- Validate the active checkpoint and transaction logs of a running file system.
- Check the integrity of a file system archive/backup (checkpoint plus a set of
  transaction logs).

Running the File System
-----------------------
In order to verify the integrity of a running file system by identifying lost
files or files with chunk placement problems, run:

    qfsfsck -m metaServer.hostname -p metaServer.port

The output will look something like this if everything is okay:

    Lost files total: 0
    Directories: 280938
    Directories reachable: 280938 100%
    Directory reachable max depth: 14
    Files: 1848149
    Files reachable: 1848149 100%
    Files reachable with recovery: 1811022 97.9911%
    Files reachable striped: 34801 1.88302%
    Files reachable sum of logical sizes: 37202811695550
    1  Files reachable lost: 0 0%
    2  Files reachable lost if server down: 0 0%
    3  Files reachable lost if rack down: 0 0%
    4  Files reachable abandoned: 0 0%
    5  Files reachable ok: 1848149 100%
    File reachable max size: 4011606632
    File reachable max chunks: 128
    File reachable max replication: 3
    Chunks: 19497647
    Chunks reachable: 19497647 100%
    Chunks reachable lost: 0 0%
    Chunks reachable no rack assigned: 0 0%
    Chunks reachable over replicated: 0 0%
    Chunks reachable under replicated: 0 0%
    Chunks reachable replicas: 22715209 116.502%
    Chunk reachable max replicas: 3
    Recovery blocks reachable: 1858706
    Recovery blocks reachable partial: 0 0%
    Fsck run time: 6.45906 sec.
    Files: [fsck_state size replication type stripes recovery_stripes
    stripe_size chunk_count mtime path]
    Filesystem is HEALTHY

When there are lost or abandoned files, and/or files with placement problems,
they will be placed in one of the four categories listed below. The number
before each category indicates the `fsck_state` associated with that category.

`1  Files reachable lost: 0 0%` # files lost, these files cannot be recovered
`2  Files reachable lost if server down: 0 0%` # these files could be lost with one chunk server down
`3  Files reachable lost if rack down: 0 0%` # this files could be lost with a rack down
`4  Files reachable abandoned: 0 0%` # file which failed to be allocated, automatically pruned

Each problem file will also be listed, prefixed by the `fsck_state` and several
other attributes, followed by the header line seen below:

`Files: [fsck_state size replication type stripes recovery_stripes stripe_size chunk_count mtime path]`

The `fsck_state` identifies which problem category a given file is in. For
example:

    1 64517075 2 2 128 0 65536 128 2012-09-22T11:36:40.597073Z /qfs/ops/jarcache/paramDB_9f68d84fac11ecfeab876844e1b71e91.sqlite.gz
    3 56433403 2 2 128 0 65536 128 2011-10-05T15:02:28.057320Z /qfs/ops/jarcache/paramDB_7912225a0775efa45e02cf0a5bb5a130.sqlite.gz
    3 55521703 2 2 128 0 65536 128 2012-08-28T15:02:07.791657Z /qfs/ops/jarcache/paramDB_f0c557f0bb36ac0375c9a8c95c0a51f8.sqlite.gz

means there is one completely lost file, and two other files that could be lost after the failure of a single rack.

Active Checkpoint and Transaction Logs
--------------------------------------
In order to validate the checkpoint and transaction logs of a running
metaserver, the *metaServer.checkpoint.lockFileName* parameter must be
configured in the metaserver (as it is used to synchronize access to the
checkpoint files and transaction logs). The lock file, if specified in
*metaServer.checkpoint.lockFileName*, will be created when the second checkpoint
is created.

**Note**: `qfsfsck` will attempt to load the file system image into memory, so
make sure there is enough memory available on the head node to do this.

To run this check:

    qfsfsck -L metaServer.checkpoint.lockFileName -l metaServer.logDir -c metaServer.cpDir

Example
-------
Given the following configuration:

    metaServer.checkpoint.lockFileName /home/qfs0/run/ckpt.lock
    metaServer.cpDir = /home/qfs0/state/checkpoint
    metaServer.logDir = /home/qfs0/state/transactions

the check would be executed like so:

    qfsfsck -L /home/qfs0/run/ckpt.lock -l /home/qfs0/state/transactions -c /home/qfs0/state/checkpoint

If everything is okay, the output will look something like this:

    09-25-2012 20:39:01.894 INFO - (restore.cc:97) restoring from checkpoint of 2012-09-25T20:00:26.971544Z
    09-25-2012 20:39:01.894 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55710
    09-25-2012 20:39:24.010 INFO - (restore.cc:97) restoring from checkpoint of 2012-09-25T20:03:09.383993Z
    09-25-2012 20:39:24.010 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55710
    09-25-2012 20:39:24.010 INFO - (replay.cc:559) log time: 2012-09-25T20:00:24.161876Z
    09-25-2012 20:39:24.010 INFO - (replay.cc:559) log time: 2012-09-25T20:09:43.533466Z
    09-25-2012 20:39:24.010 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55711
    09-25-2012 20:39:24.011 INFO - (replay.cc:559) log time: 2012-09-25T20:09:43.533721Z
    09-25-2012 20:39:24.011 INFO - (replay.cc:559) log time: 2012-09-25T20:19:43.829361Z
    09-25-2012 20:39:24.011 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55712
    09-25-2012 20:39:24.011 INFO - (replay.cc:559) log time: 2012-09-25T20:19:43.829674Z
    09-25-2012 20:39:24.012 INFO - (replay.cc:559) log time: 2012-09-25T20:29:44.712673Z

otherwise `qfsfsck` will exit in error.

File System Archive
-------------------
Checking a file system image backup is very similar to that of checking a
running metaserver's checkpoint and transaction logs, except no lock file
(*metaServer.checkpoint.lockFileName*) is required. The backup must be
extracted to the same set of paths from which it was archived. Therefore it
should be extracted to the location specified by *metaServer.cpDir* and
*metaServer.logDir* of its associated metaserver.

Example
-------
Given the following configuration:

    metaServer.cpDir = /home/qfs0/state/checkpoint
    metaServer.logDir = /home/qfs0/state/transactions

and an archive located at: `/foo/bar/qfs0-backup-31-23.tar.gz` created from
`/home/qfs0/state`

The following commands can be used to verify the backup:

    mkdir -p /home/qfs0/state
    cd /home/qfs0/state
    tar -xzf /foo/bar/qfs0-backup-31-23.tar.gz
    qfsfsck /home/qfs0/state/transactions -c /home/qfs0/state/checkpoint

If everything is okay, the output will look something like this:

    09-25-2012 20:39:01.894 INFO - (restore.cc:97) restoring from checkpoint of 2012-09-25T20:00:26.971544Z
    09-25-2012 20:39:01.894 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55710
    09-25-2012 20:39:24.010 INFO - (restore.cc:97) restoring from checkpoint of 2012-09-25T20:03:09.383993Z
    09-25-2012 20:39:24.010 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55710
    09-25-2012 20:39:24.010 INFO - (replay.cc:559) log time: 2012-09-25T20:00:24.161876Z
    09-25-2012 20:39:24.010 INFO - (replay.cc:559) log time: 2012-09-25T20:09:43.533466Z
    09-25-2012 20:39:24.010 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55711
    09-25-2012 20:39:24.011 INFO - (replay.cc:559) log time: 2012-09-25T20:09:43.533721Z
    09-25-2012 20:39:24.011 INFO - (replay.cc:559) log time: 2012-09-25T20:19:43.829361Z
    09-25-2012 20:39:24.011 INFO - (replay.cc:63) open log file: /home/qfs0/state/transactions/log.55712
    09-25-2012 20:39:24.011 INFO - (replay.cc:559) log time: 2012-09-25T20:19:43.829674Z
    09-25-2012 20:39:24.012 INFO - (replay.cc:559) log time: 2012-09-25T20:29:44.712673Z

otherwise `qfsfsck` will exit in error.

WORM Mode
---------
WORM (or Write Once, Read Many) is a special file system mode which makes it
imposible to delete files from the file system. This feature is useful for
protecting critical data from deletion. The `qfstoggleworm` tool is used to
turn WORM mode on and off.

To turn WORM mode on do the following:

`qfstoggleworm -s metaServer.host -p metaServer.port -t 1`

Likewise, to turn WORM mode off do the following:

`qfstoggleworm -s metaServer.host -p metaServer.port -t 0`

When a QFS instance is running in WORM mode, a file can only be created if it
ends with a `.tmp` suffix. Once stored in the file system, it can then be
renamed without the `.tmp` suffix. For example, to write `foo.bar` to a QFS
instance running in WORM mode, it would have to be created as `foo.bar.tmp` then
moved into place as `foo.bar`. Once moved, the file cannot be deleted or
modified, unless WORM mode is disabled.

Web Reporting Interface
=======================
The QFS web interface `qfsstatus.py` provides a rich set of real-time
information, which can be used monitor file system instances.

Configuration
-------------
The web server configuration is normally stored in a file called `webUI.cfg`.
See the [[Configuration Reference]] for a complete set of web UI configuration
parameters. Also the sample servers used in the examples include a typical web
UI configuration. Running

`qfsstatus.py /path/to/webUI.cfg`

Interface
---------
The following sample image of the web reporting interface is for a QFS instance
with 1 metaserver and 1 chunk server, configured with three chunk directories.
The host file system size is ~~18GB, out of which ~~10GB is used (not by QFS)
and ~~8GB is available.

![QFS WebUI](images/Administrator's Guide/qfs-webui.png)

The following table describes some UI elements:

| vega:20000 | The metaserver host name and port number. |
| ---------- | ----------------------------------------- |
| Chunk Servers Status | Opens a page with chunk servers statistics. One could select various chunk server parameters to be displayed, the refresh interval and delta. |
| Metaserver Status | Opens a page with metaserver statistics. One could select various metaserver parameters to be displayed, the refresh interval and delta. |
| Total space | Total space of the host file system(s) where QFS stores chunks. |
| Used space | Space used by QFS. |
| Free space | Available space in the host file system(s) where QFS stores chunks. When the free space becomes less than a % threshold (given by a metaserver configuration value) the metaserver stops using this chunk directory for chunk placement.|
| WORM mode | Status of the write-once-read-many mode. |
| Nodes | Number of nodes in different states in the file system. |
| Replications | Number of chunks in various states of replication. |
| Allocations | File system-wide count of QFS clients, chunk servers, and so on. |
| Allocations b+tree | Internal b+tree counters. In this example, root directory + dumpster directory make up the 2 in fattr. |
| Chunk placement candidates | Out of all chunk servers, how many are used for chunk placement, which are assigned racks. |
| Disks | Number of disks in the file system. **Note**: our recommendation is to use one chunk directory per physical disk. |
| All Nodes | Table of one row per chunk server, describing a summary for each chunk server. | PING

A metaserver or chunk server ping can be used to dump the file system status.
All of the information presented by the web interface is available via a ping.
This makes it fairly easy to build automation around a QFS file system.

You can use `qfsping` to ping a metaserver:

`qfsping -m -s metaServer.hostname -p metaServer.portg`

The command is similar for a chunk server ping:

`qfsping -c -s chunkServer.hostname -p chunkServer.port`

Parsing the output of a ping is beyond the scope of this document but the Python
web interface `qfsstatus.py` provides an example of this and more.

Chunk Server
============
The chunk server is the workhorse of QFS file system and is responsible for
storing and retrieving file chunk data. This section will discuss basic chunk
server administration.

Configuration
-------------
The chunk server configuration is normally stored in a file called
`ChunkServer.prp`. The [[Deployment Guide]] includes several minimal sample
configurations. For the complete set of configuration parameters see the
[[Configuration Reference]].

Running
-------
`chunkserver /path/to/ChunkServer.prp`

Hibernation
-----------
Hibernation is used to temporarily take a chunk server offline, such as for
maintenance of the physical server. When a chunk server is hibernated, the
metaserver will not actively attempt to re-replicate or recover chunks hosted by
the hibernated chunk server for the specified hibernation period. However,
chunks will be passively recovered if they're necessary to fulfill a request.

This feature is useful in preventing replication/recovery storms when performing
node or rack level maintenance.

qfshibernate
------------
The `qfshibernate` tool is used to hibernate a chunk server:

`qfshibernate -m chunkServer.metaServer.hostname -p chunkServer.metaServer.port -c chunkServer.hostname -d chunkServer.clientPort -s delay (in seconds)`

Example
-------
Given the following metaserver configuration:

    chunkServer.metaServer.hostname = 192.168.1.1
    chunkServer.metaServer.port = 10000

To hibernate a chunk server at 192.168.10.20 (*chunkServer.hostname*) running on
a client port of 1635 (*chunkServer.clientPort*) for 30 minutes one would
execute the following command:

    qfshibernate -m 192.168.1.1 -p 10000 -c 192.168.10.20 -d 1635 -s 1800

This would instruct the metaserver at 192.168.1.1:10000 to hibernate the chunk
server at 192.168.10.20:1635 for 1800 seconds or 30 minutes. Upon hibernation
the chunk server will exit.

Notes
-----
- Currently the only way to extend a hibernation window is to restore the given
  chunk server and re-hibernate it.
- The longer the hibernation window, the greater the likelihood of data loss. A
  window of no more than an hour is recommended for this reason.

Evacuation
----------
Evacuation can be used to permanently or temporarily retire a chunk server
volume. It is recommended that evacuation be used instead of hibernation if the
expected down time exceeds one hour.

To evacuate a chunk server, create a file named *evacuate* in each of its chunk
directories (*chunkServer.chunkDir*). This will cause the chunk server to
safely remove all chunks from each chunk directory where the *evacuate* file is
present. Once a chunk directory is evacuated, the chunk server will rename the
*evacuate* file to *evacuate.done*.

Example
-------
To evacuate a chunk server with following chunk directories configured:

`chunkServer.chunkDir /mnt/data0/chunks /mnt/data1/chunks /mnt/data2/chunks`

one could use the following script:

    1.!/bin/bash
    for data in /mnt/data*; do
        chunkdir=$data/chunks
        if [ -e $chunkdir ]; then
            touch $chunkdir/evacuate
        fi
    done

This will cause the chunk server to evacuate all chunks from
`/mnt/data0/chunks`, `/mnt/data1/chunks`, and `/mnt/data2/chunks`. As each chunk
directory is evacuated, the chunk server will rename its *evacuate* file to
*evacuate.done*.

To check the status of the evacuation:

`cd /mnt && find -name evacuate.done | wc -l`

Once the count returned equals 3, all chunk directories have been evacuated and
it's safe to stop the chunk server.

**Note**: the metaserver web UI will also list all chunk server evacuations and
their status.

Client Tools
============
QFS includes a set of client tools to make it easy to access the file system.
This section describes those tools.

| Tool | Purpose | Notes |
| ---- | ------- | ----- |
|`cpfromqfs`| Copy files from QFS to a local file system or to stdout | Supported options: skipping holes, setting of write buffer size, start and end offsets of source file, read ahead size, op retry count, retry delay and retry timeouts, partial sparse file support. See `./cpfromqfs -h` for more.|
|`cptoqfs`| Copy files from a local file system or stdin to QFS | Supported options: setting replication factor, data and recovery stripe counts, stripes size, input buffer size, QFS write buffer size, truncate/delete target files, create exclusive mode, append mode, op retry count, retry delay and retry timeouts. See `./cptoqfs -h` for more.|
|`qfscat`| Output the contents of file(s) to stdout | See `./qfscat -h` for more information.|
|`qfsput`| Reads from stdin and writes to a given QFS file |See `./qfsput -h` for more information.|
|`qfsdataverify`| Verify the replication data of a given file in QFS| The `-c` option compares the checksums of all replicas. The `-d` option verifies that all N copies of each chunk are identical. Note that for files with replication 1, this tool performs **no** verification. See `./qfsdataverify -h` for more.|
|`qfsfileenum`| Prints the sizes and locations of the chunks for the given file| See `./qfsfileenum -h` for more information.|
|`qfsping`| Send a ping to metaserver or chunk server | Doing a metaserver ping returns list of chunk servers that are up and down. It also returns the usage stats of each up chunk server.\\Doing a chunk server ping returns a the chunk server stats. See `./qfsping -h` for more.|
|`qfshibernate`| Hibernates a chunk server for the given number of seconds | See `./qfshibernate -h` for more information.|
|`qfsshell`| Opens a simple client shell to execute QFS commands | By default this opens an interactive shell. One can bypss the interactive shell and execute commands directly by using the `-q` option. See `./qfsshell -h` for more.|
|`qfsstats`|Reports qfs statistics | The `-n` option is used to control the interval between reports. The RPC stats are also reported if the `-t` option is used. See `./qfsstats -h` for more.|
|`qfstoggleworm`|Set the WORM (write once read many) mode of the file system | |

Related Documents
=================
- [[Deployment Guide]]
- [[Configuration Reference]]


![Quantcast](//pixel.quantserve.com/pixel/p-9fYuixa7g_Hm2.gif?labels=opensource.qfs.wiki)
