package net.opentsdb.deferred;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;

import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;

import java.io.IOException;


public class HBaseDeferredClient {

    private Configuration config;
    public Connection connection;
    public BufferedMutator mutator;

    /* Up to how many milliseconds can we buffer an edit on the client side.  */
    private volatile short flush_interval = 1000; //ms

    /* Factory through which we will create all its channels / sockets. */
    private ClientSocketChannelFactory channel_factory = null;

    /* Watcher to keep track of the -ROOT- region in ZooKeeper */
    private ZKClient zkclient = null;

    /* 
     * Constructor.
     */
    public HBaseDeferredClient(Configuration conf) throws IOException {
        config = conf;
        connection = ConnectionFactory.createConnection(config);
    }

    /*
     * Constructor.
     */
    public HBaseDeferredClient(final String quorum_spec) {
        this(quorum_spec, "/hbase");
    }

    /*
     * Constructor.
     */
    public HBaseDeferredClient(final String quorum_spec, final String base_path) {
        this(quorum_spec, base_path, defaultChannelFactory());
    }


    /* Creates a default channel factory in case we haven't been given one. */
    private static NioClientSocketChannelFactory defaultChannelFactory() {
        final Executor executor = Executors.newCachedThreadPool();
        return new NioClientSocketChannelFactory(executor, executor);
    }

    /*
     * Constructor.
     */
    public HBaseDeferredClient(final String quorum_spec, final String base_path,
                               final ClientSocketChannelFactory channel_factory) {
        this.channel_factory = channel_factory;
        zkclient = new ZKClient(quorum_spec, base_path);
    }

    /* BufferedMutator is assigned to a table, so I assume we need
        multiple mutators for different tables */
    public void initMutatorForTable(TableName table) throws IOException {
        BufferedMutatorParams params = new BufferedMutatorParams(table);
        mutator = this.connection.getBufferedMutator(params);
    }

    public Configuration getConfig() {
        return this.config;
    }

    public Deferred<Object> flush() {
        return null;
    }

    public Deferred<Object> put(final PutRequest request) {
        return sendRpcToRegion(request);
    }

    public Deferred<Object> put(final Put p) {
        /* STEP 1
         * Create a Deferred object before we start the async operation */
        final Deferred<Object> d = new Deferred<Object>();

        /* STEP 4. This is a very naive implementation of async operations.
         * I assume we need to use some kind of thread pool here instead
         * of creating one thread for every request.
         */
        final class AsyncThread extends Thread {
            public void run() {
                try {
                    mutator.mutate(p);

                    /* There could be a separate thread flushing mutator periodically
                     * to benefit from local buffering
                     */
                    mutator.flush();

                    /* This actually triggers callback chain.
                     * mutate doesn't return any result, but we can define some
                     * return codes like SUCCESS_PUT, SUCCESS_DELETE, etc
                     */
                    d.callback(null);
                } catch (IOException e) {
                    /* Exception handling should be aware of Hbase specific exceptions, like
                     * RetriesExhaustedWithDetailsException
                     */

                    /* This triggers Errback chain */
                    d.callback(e);
                }
            }

        }
        /* STEP 2. Initiate async operation in a separate thread */
        new AsyncThread().start();

        /* STEP 3. Return Deferred object to caller. Async process my not finish yet */
        return d;
    }

    private final class ZKClient implements Watcher {

        private final String quorum_spec;

        private final String base_path;

        private ZooKeeper zk;

        public ZKClient(final String quorum_spec, final String base_path) {
            this.quorum_spec = quorum_spec;
            this.base_path = base_path;
        }

        public void process(final WatchedEvent event) {
        }
    }

}
