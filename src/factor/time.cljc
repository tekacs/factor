(ns factor.time
  "Date and Time based on juxt/tick, as well as types for CLJ+S"
  (:require [cognitect.transit :as transit]
            [factor.types :as ty]
            [tick.alpha.api :as tick]
            [tick.locale-en-us]
            #?(:cljs [java.time :refer [Period
                                        LocalDate
                                        LocalDateTime
                                        ZonedDateTime
                                        OffsetTime
                                        Instant
                                        OffsetDateTime
                                        ZoneId
                                        DayOfWeek
                                        LocalTime
                                        Month
                                        Duration
                                        Year
                                        YearMonth]])
            [time-literals.read-write :as read-write])
  #?(:clj (:import (java.time Period
                              LocalDate
                              LocalDateTime
                              ZonedDateTime
                              OffsetTime
                              Instant
                              OffsetDateTime
                              ZoneId
                              DayOfWeek
                              LocalTime
                              Month
                              Duration
                              Year
                              YearMonth))))

(ty/def :time/period [::ty/instance Period])
(ty/def :time/local-date [::ty/instance LocalDate])
(ty/def :time/local-date-time [::ty/instance LocalDateTime])
(ty/def :time/zoned-date-time [::ty/instance ZonedDateTime])
(ty/def :time/offset-time [::ty/instance OffsetTime])
(ty/def :time/instant [::ty/instance Instant])
(ty/def :time/offset-date-time [::ty/instance OffsetDateTime])
(ty/def :time/zone-id [::ty/instance ZoneId])
(ty/def :time/day-of-week [::ty/instance DayOfWeek])
(ty/def :time/local-time [::ty/instance LocalTime])
(ty/def :time/month [::ty/instance Month])
(ty/def :time/duration [::ty/instance Duration])
(ty/def :time/year [::ty/instance Year])
(ty/def :time/year-month [::ty/instance YearMonth])

(ty/def :time/->date [:fn tick/date])

(def ^:private time-classes
  {'period           Period
   'date             LocalDate
   'date-time        LocalDateTime
   'zoned-date-time  ZonedDateTime
   'offset-time      OffsetTime
   'instant          Instant
   'offset-date-time OffsetDateTime
   'time             LocalTime
   'duration         Duration
   'year             Year
   'year-month       YearMonth
   'zone             ZoneId
   'day-of-week      DayOfWeek
   'month            Month})

(def write-handlers
  (->> time-classes
       (map (fn [[tick-class host-class]] [host-class (transit/write-handler (constantly (str "time/" (name tick-class))) str)]))
       (into {})))

(def read-handlers
  (->> read-write/tags
       (map (fn [[sym fun]] [(str "time/" (name sym)) (transit/read-handler fun)]))
       (into {})))
