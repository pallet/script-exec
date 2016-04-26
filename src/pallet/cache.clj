(ns pallet.cache
  "A caching interface and implementations"
  (:require
   [pallet.cache.impl :as impl]))

;; Based on fogus' clache protocol of the same name.
;; Adds an expiry callback, enabling use with resource holding values.
;; Assumes mutable fields, to enable use of cache from multiple threads.
(defprotocol CacheProtocol
  "Cache interface."
  (miss [cache e ret] [cache map-entry]
    "Is meant to be called if the cache is determined to **not** contain a
     value associated with `e`")
  (expire [cache e]
    "Expire a key from the cache")
  (expire-all [cache]
    "Expires all items in the cache"))

;; Implementation macro. Based on macro of the same name from clache.
;; Adds a factory function for use with clojure 1.2
(defmacro ^{:private true} defcache
  [cache-name fields & specifics]
  `(deftype ~cache-name [~@fields]
     ~@specifics

     clojure.lang.ILookup
     (valAt [this# key#]
       (impl/lookup this# key#))
     (valAt [this# key# not-found#]
       (if-let [res# (impl/lookup this# key#)]
         res#
         not-found#))

     clojure.lang.IPersistentMap
     (assoc [this# k# v#]
       (miss this# k# v#))
     (without [this# k#]
       (expire this# k#))

     clojure.lang.Associative
     (containsKey [this# k#]
       (impl/has? this# k#))
     (entryAt [this# k#]
       (impl/lookup this# k#))))

(defn- validate-fifo [cache queue limit]
  (assert (>= limit (count cache))
          (str "cache has correct size: " limit " != " (count cache)))
  (assert (= limit (count queue))
          (str "queue has correct size: " limit " != " (count cache)))
  (doseq [k (.seq queue)
          :when (not= ::free k)]
    (assert (get cache k) (str "cache missing value for " (pr-str k)))))

(defn- evict
  "Evict an item from a cache map and queue."
  [cache queue item]
  (dosync
   (let [res (get @cache item)]
     (when res
       (alter cache dissoc item)
       (alter queue #(->> (concat (remove (fn [x] (= item x)) %)
                                  (repeat ::free))
                          (take (count @queue))
                          (into clojure.lang.PersistentQueue/EMPTY)))))))

(defcache FIFOCache [cache queue limit expire-f]
  impl/CacheProtocolImpl
  (lookup
      [_ item]
      (dosync
       (let [res (get @cache item)]
         (when res
           (evict cache queue item))
         res)))
  (lookup
      [_ item default]
      (dosync
       (let [res (get @cache item default)]
         (when res
           (evict cache queue item))
         res)))
  (has?
   [_ item]
   (contains? @cache item))

  CacheProtocol
  (miss [_ item result]
   ;; (assert result)
   ;; (validate-fifo @cache @queue limit)
   (let [[not-free? v] (dosync
                        (let [k (peek @queue)
                              not-free? (not= ::free k)
                              v (when not-free? (get @cache k))]
                          (alter cache #(-> % (dissoc k) (assoc item result)))
                          (alter queue #(-> % pop (conj item)))
                          [not-free? v]))]
     (when (and expire-f not-free?)
       (assert v)
       (expire-f v))
     ;; (validate-fifo @cache @queue limit)
     nil))

  (miss [this [item result]] (miss this item result))

  (expire-all
   [_]
   ;; (validate-fifo @cache @queue limit)
   (let [c (dosync
            (let [c @cache]
              (alter
               queue
               (constantly
                (into clojure.lang.PersistentQueue/EMPTY
                      (repeat limit ::free))))
              (alter cache (constantly {}))
              c))]
     (when expire-f
       (doseq [[_ v] c]
         (expire-f v)))
     ;; (validate-fifo @cache @queue limit)
     nil))

  (expire
   [_ item]
   ;; (validate-fifo @cache @queue limit)
   (let [v (dosync
            (let [v (get @cache item ::miss)]
              (when (not= ::miss v)
                (println "expire" v)
                (evict cache queue item))
              ;; (validate-fifo @cache @queue limit)
              v))]
     (when (and expire-f (not= v ::miss))
       (expire-f v)))
   ;; (validate-fifo @cache @queue limit)
   nil)

  Object
  (toString [_]
            (str @cache \, \space (pr-str @queue)))

  clojure.lang.Counted
  (count
   [this#]
   (count @cache))

  clojure.lang.IPersistentCollection
  ;; (cons [this# elem#] (cons (-base this#) elem#))
  (empty [this#] (expire-all this#))
  ;; (equiv [this# other#] (.equiv this# other#))

  clojure.lang.Seqable
  (seq [this#] (seq @cache))

  ;; Java interfaces
  java.lang.Iterable
  (iterator [this#] (.iterator ^Iterable @cache)))

(defn make-fifo-cache [& {:keys [cache queue limit expire-f]
                          :or {limit 10}}]
  (FIFOCache.
   (ref (or cache {}))
   (ref (or queue
            (into clojure.lang.PersistentQueue/EMPTY (repeat limit ::free))))
   limit
   expire-f))
