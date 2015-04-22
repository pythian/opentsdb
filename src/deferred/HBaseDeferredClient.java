package net.opentsdb.deferred;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;

import java.io.IOException;


public class HBaseDeferredClient {

    private Configuration config;
    public Connection connection;
    public BufferedMutator mutator;

    public HBaseDeferredClient(Configuration conf) throws IOException {
        config = conf;
        connection = ConnectionFactory.createConnection(config);
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

    public Deferred<Object> put(final Put p) {
        /* STEP 1
           Create a Deferred object before we start the async operation */
        final Deferred<Object> d = new Deferred<Object>();

        /* STEP 4. This is a very naive implementation of async operations.
            I assume we need to use some kind of thread pool here instead
            of creating one thread for every request.
         */
        final class AsyncThread extends Thread {
            public void run() {
                try {
                    mutator.mutate(p);

                    /* There could be a separate thread flushing mutator periodically
                       to benefit from local buffering
                     */
                    mutator.flush();

                    /* This actually triggers callback chain.
                       mutate doesn't return any result, but we can define some
                       return codes like SUCCESS_PUT, SUCCESS_DELETE, etc
                     */
                    d.callback(null);
                } catch (IOException e) {
                    /* Exception handling should be aware of Hbase specific exceptions, like
                        RetriesExhaustedWithDetailsException
                     */
                    // LOG.error("Put failed: " + e.getMessage());

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

}
