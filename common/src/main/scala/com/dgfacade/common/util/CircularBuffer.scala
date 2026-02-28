/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.util

import java.time.Instant
import java.time.Duration
import scala.collection.mutable

/**
 * A thread-safe circular buffer that evicts entries older than maxAge
 * and caps at maxSize. Used to hold HandlerState records in memory
 * for the monitoring UI (max 1000 entries, max 1 hour).
 */
class CircularBuffer[T](maxSize: Int = 1000, maxAgeDuration: Duration = Duration.ofHours(1)) {

  private val buffer: mutable.ArrayDeque[TimestampedEntry[T]] = mutable.ArrayDeque.empty
  private val lock = new Object

  case class TimestampedEntry[A](value: A, timestamp: Instant)

  def add(item: T): Unit = lock.synchronized {
    evictOld()
    if (buffer.size >= maxSize) {
      buffer.removeHead()
    }
    buffer.addOne(TimestampedEntry(item, Instant.now()))
  }

  def getAll: java.util.List[T] = lock.synchronized {
    evictOld()
    val result = new java.util.ArrayList[T]()
    buffer.foreach(e => result.add(e.value))
    result
  }

  def size: Int = lock.synchronized {
    evictOld()
    buffer.size
  }

  def clear(): Unit = lock.synchronized {
    buffer.clear()
  }

  def find(predicate: T => Boolean): Option[T] = lock.synchronized {
    evictOld()
    buffer.find(e => predicate(e.value)).map(_.value)
  }

  private def evictOld(): Unit = {
    val cutoff = Instant.now().minus(maxAgeDuration)
    while (buffer.nonEmpty && buffer.head.timestamp.isBefore(cutoff)) {
      buffer.removeHead()
    }
  }
}
