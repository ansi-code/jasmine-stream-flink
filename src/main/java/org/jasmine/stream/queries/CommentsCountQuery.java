package org.jasmine.stream.queries;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.jasmine.stream.models.CommentHourlyCount;
import org.jasmine.stream.models.CommentInfo;
import org.jasmine.stream.models.CommentType;
import org.jasmine.stream.operators.CollectorAggregateFunction;
import org.jasmine.stream.operators.CounterAggregateFunction;
import org.jasmine.stream.operators.CounterReduceFunction;
import org.jasmine.stream.operators.TimestampEnrichProcessAllWindowFunction;
import org.jasmine.stream.utils.DateUtils;

import java.util.Calendar;

public class CommentsCountQuery {
    @SuppressWarnings("Duplicates")
    public static DataStream<CommentHourlyCount> run(DataStream<CommentInfo> inputStream, Time window) {
        return inputStream
                .filter(item -> item.getCommentType() == CommentType.COMMENT)
                .map(item -> {
                    Calendar calendar = DateUtils.parseCalendar(item.getCreateDate());
                    return (int) Math.floor(calendar.get(Calendar.HOUR_OF_DAY) / 2.0);
                })
                .keyBy(s -> s)
                .window(TumblingEventTimeWindows.of(window))
                .aggregate(new CounterAggregateFunction<>())
                .windowAll(TumblingEventTimeWindows.of(window))
                .aggregate(new CollectorAggregateFunction<>(), new TimestampEnrichProcessAllWindowFunction<>())
                .map(item -> new CommentHourlyCount(item.getTimestamp(), item.getElement()));
    }

    @SuppressWarnings("Duplicates")
    public static Tuple3<DataStream<CommentHourlyCount>, DataStream<CommentHourlyCount>, DataStream<CommentHourlyCount>> runAll(DataStream<CommentInfo> inputStream) {
        Time window24h = Time.hours(24);
        Time window7d = Time.days(7);
        Time window1M = Time.days(30);

        DataStream<Tuple2<Integer, Long>> intermediateWindow24hStream = inputStream
                .filter(item -> item.getCommentType() == CommentType.COMMENT)
                .map(item -> {
                    Calendar calendar = DateUtils.parseCalendar(item.getCreateDate());
                    return (int) Math.floor(calendar.get(Calendar.HOUR_OF_DAY) / 2.0);
                })
                .keyBy(s -> s)
                .window(TumblingEventTimeWindows.of(window24h))
                .aggregate(new CounterAggregateFunction<>());

        DataStream<Tuple2<Integer, Long>> intermediateWindow7dStream = intermediateWindow24hStream
                .keyBy(s -> s.f0)
                .window(TumblingEventTimeWindows.of(window7d))
                .reduce(new CounterReduceFunction<>());

        DataStream<Tuple2<Integer, Long>> intermediateWindow1MStream = intermediateWindow7dStream
                .keyBy(s -> s.f0)
                .window(TumblingEventTimeWindows.of(window1M))
                .reduce(new CounterReduceFunction<>());

        DataStream<CommentHourlyCount> window24hStream = intermediateWindow24hStream
                .windowAll(TumblingEventTimeWindows.of(window24h))
                .aggregate(new CollectorAggregateFunction<>(), new TimestampEnrichProcessAllWindowFunction<>())
                .map(item -> new CommentHourlyCount(item.getTimestamp(), item.getElement()));

        DataStream<CommentHourlyCount> window7dStream = intermediateWindow7dStream
                .windowAll(TumblingEventTimeWindows.of(window7d))
                .aggregate(new CollectorAggregateFunction<>(), new TimestampEnrichProcessAllWindowFunction<>())
                .map(item -> new CommentHourlyCount(item.getTimestamp(), item.getElement()));

        DataStream<CommentHourlyCount> window1MStream = intermediateWindow1MStream
                .windowAll(TumblingEventTimeWindows.of(window1M))
                .aggregate(new CollectorAggregateFunction<>(), new TimestampEnrichProcessAllWindowFunction<>())
                .map(item -> new CommentHourlyCount(item.getTimestamp(), item.getElement()));

        return new Tuple3<>(window24hStream, window7dStream, window1MStream);
    }
}
