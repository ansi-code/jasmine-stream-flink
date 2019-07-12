/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jasmine.stream;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.jasmine.stream.config.Configuration;
import org.jasmine.stream.models.CommentInfo;
import org.jasmine.stream.models.Top3Article;
import org.jasmine.stream.queries.TopArticlesQuery;
import org.jasmine.stream.utils.JNStreamExecutionEnvironment;
import org.jasmine.stream.utils.JSONClassDeserializationSchema;

import java.util.Objects;
import java.util.Properties;

public class TopArticlesJob {

    @SuppressWarnings("Duplicates")
    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = JNStreamExecutionEnvironment.getExecutionEnvironment();

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", Configuration.getParameters().get("kafka-server"));
        properties.setProperty("group.id", Configuration.getParameters().get("kafka-group-id"));
        properties.setProperty("auto.offset.reset", "latest");

        DataStream<CommentInfo> inputStream = env
                .addSource(new FlinkKafkaConsumer<>(Configuration.getParameters().get("kafka-input-topic"), new JSONClassDeserializationSchema<>(CommentInfo.class), properties))
                .filter(Objects::nonNull)
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<CommentInfo>() {
                    @Override
                    public long extractAscendingTimestamp(CommentInfo commentInfo) {
                        return commentInfo.getCreateDate() * 1000;
                    }
                });

        // Query 1
        DataStream<Top3Article> topArticles1h = TopArticlesQuery.run(inputStream, Time.hours(1));
        //DataStream<Top3Article> topArticles24h = TopArticlesQuery.run(inputStream, Time.hours(24));
        //DataStream<Top3Article> topArticles7d = TopArticlesQuery.run(inputStream, Time.days(7));
/*
        Tuple3<DataStream<Top3Article>, DataStream<Top3Article>, DataStream<Top3Article>> topArticlesStreams = TopArticlesQuery.runAll(inputStream);
        DataStream<Top3Article> topArticles1h = topArticlesStreams.f0;
        DataStream<Top3Article> topArticles24h = topArticlesStreams.f1;
        DataStream<Top3Article> topArticles7d = topArticlesStreams.f2;

        topArticles1h.addSink(new FlinkKafkaProducer<>(String.format(Configuration.getParameters().get("kafka-output-topic"), "topArticles1h"), new JSONClassSerializationSchema<>(), properties));
        topArticles24h.addSink(new FlinkKafkaProducer<>(String.format(Configuration.getParameters().get("kafka-output-topic"), "topArticles24h"), new JSONClassSerializationSchema<>(), properties));
        topArticles7d.addSink(new FlinkKafkaProducer<>(String.format(Configuration.getParameters().get("kafka-output-topic"), "topArticles7d"), new JSONClassSerializationSchema<>(), properties));

        topArticles1h.print();
        topArticles24h.print();
        topArticles7d.print();
        */
        topArticles1h.print();

        // execute program
        env.execute("JASMINE Stream");
    }
}
