;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.neanderthal.internal.host.buffer-block
  (:require [vertigo
             [core :refer [wrap]]
             [bytes :refer [direct-buffer byte-seq slice-buffer]]
             [structs :refer [float64 float32 int32 int64 wrap-byte-seq]]]
            [uncomplicate.commons.core
             :refer [Releaseable release let-release clean-buffer double-fn
                     wrap-float wrap-double wrap-int wrap-long]]
            [uncomplicate.fluokitten.protocols
             :refer [PseudoFunctor Functor Foldable Magma Monoid Applicative fold]]
            [uncomplicate.neanderthal
             [core :refer [transfer! copy! subvector]]
             [real :refer [entry entry!]]
             [math :refer [ceil abs]]]
            [uncomplicate.neanderthal.internal
             [api :refer :all]
             [common :refer [dense-rows dense-cols dense-dias region-dias region-cols region-rows
                             dragan-says-ex real-accessor]]
             [printing :refer [print-vector print-ge print-uplo print-banded print-packed]]
             [navigation :refer :all]]
            [uncomplicate.neanderthal.internal.host.fluokitten :refer :all])
  (:import [java.nio ByteBuffer DirectByteBuffer]
           [clojure.lang Seqable IFn IFn$DD IFn$DDD IFn$DDDD IFn$DDDDD IFn$LD IFn$LLD IFn$L IFn$LL
            IFn$LDD IFn$LLDD IFn$LLL]
           [vertigo.bytes ByteSeq]
           [uncomplicate.neanderthal.internal.api BufferAccessor RealBufferAccessor IntegerBufferAccessor
            VectorSpace Vector RealVector Matrix IntegerVector DataAccessor RealChangeable IntegerChangeable
            RealNativeMatrix RealNativeVector IntegerNativeVector DenseStorage FullStorage RealDefault LayoutNavigator
            RealLayoutNavigator Region MatrixImplementation GEMatrix UploMatrix BandedMatrix PackedMatrix]));;TODO clean up

(defn ^:private hash* ^double [^double h ^double x]
  (double (clojure.lang.Util/hashCombine h (Double/hashCode x))))

(def ^:private f* (double-fn *))

(defn ^:private require-trf []
  (throw (ex-info "Please do the triangular factorization of this matrix first." {})))

(extend-type DirectByteBuffer
  Releaseable
  (release [this]
    (clean-buffer this)))

;; ================== Declarations ============================================

(declare integer-block-vector)
(declare real-block-vector)
(declare real-ge-matrix)
(declare real-uplo-matrix)
(declare real-banded-matrix)
(declare real-packed-matrix)

;; ============ Real Buffer ====================================================

(deftype FloatBufferAccessor []
  DataAccessor
  (entryType [_]
    Float/TYPE)
  (entryWidth [_]
    Float/BYTES)
  (count [_ b]
    (quot (.capacity ^ByteBuffer b) Float/BYTES))
  (createDataSource [_ n]
    (direct-buffer (* Float/BYTES n)))
  (initialize [_ b]
    b)
  (initialize [this b v]
    (let [v (double v)
          strd Float/BYTES]
      (dotimes [i (.count this b)]
        (.putFloat ^ByteBuffer b (* strd i) v))
      b))
  (wrapPrim [_ s]
    (wrap-float s))
  DataAccessorProvider
  (data-accessor [this]
    this)
  MemoryContext
  (compatible? [this o]
    (let [da (data-accessor o)]
      (or (identical? this da) (instance? FloatBufferAccessor da))))
  BufferAccessor
  (toSeq [this buf offset stride]
    (if (< offset (.count this buf))
      (wrap-byte-seq float32 (* Float/BYTES stride) (* Float/BYTES offset) (byte-seq buf))
      (list)))
  (slice [_ buf k l]
    (slice-buffer buf (* Float/BYTES k) (* Float/BYTES l)))
  RealBufferAccessor
  (get [_ buf i]
    (.getFloat buf (* Float/BYTES i)))
  (set [_ buf i val]
    (.putFloat buf (* Float/BYTES i) val)))

(def float-accessor (->FloatBufferAccessor))

(deftype DoubleBufferAccessor []
  DataAccessor
  (entryType [_]
    Double/TYPE)
  (entryWidth [_]
    Double/BYTES)
  (count [_ b]
    (quot (.capacity ^ByteBuffer b) Double/BYTES))
  (createDataSource [_ n]
    (direct-buffer (* Double/BYTES n)))
  (initialize [_ b]
    b)
  (initialize [this b v]
    (let [v (double v)
          strd Double/BYTES]
      (dotimes [i (.count this b)]
        (.putDouble ^ByteBuffer b (* strd i) v))
      b))
  (wrapPrim [_ s]
    (wrap-double s))
  DataAccessorProvider
  (data-accessor [this]
    this)
  MemoryContext
  (compatible? [this o]
    (let [da (data-accessor o)]
      (or (identical? this da) (instance? DoubleBufferAccessor da))))
  BufferAccessor
  (toSeq [this buf offset stride]
    (if (< offset (.count this buf))
      (wrap-byte-seq float64 (* Double/BYTES stride) (* Double/BYTES offset) (byte-seq buf))
      (list)))
  (slice [_ buf k l]
    (slice-buffer buf (* Double/BYTES k) (* Double/BYTES l)))
  RealBufferAccessor
  (get [_ buf i]
    (.getDouble buf (* Double/BYTES i)))
  (set [_ buf i val]
    (.putDouble buf (* Double/BYTES i) val)))

(def double-accessor (->DoubleBufferAccessor))

(deftype IntBufferAccessor []
  DataAccessor
  (entryType [_]
    Integer/TYPE)
  (entryWidth [_]
    Integer/BYTES)
  (count [_ b]
    (quot (.capacity ^ByteBuffer b) Integer/BYTES))
  (createDataSource [_ n]
    (direct-buffer (* Integer/BYTES n)))
  (initialize [_ b]
    b)
  (initialize [this b v]
    (let [v (double v)
          strd Integer/BYTES]
      (dotimes [i (.count this b)]
        (.putInt ^ByteBuffer b (* strd i) v))
      b))
  (wrapPrim [_ s]
    (wrap-int s))
  DataAccessorProvider
  (data-accessor [this]
    this)
  MemoryContext
  (compatible? [this o]
    (let [da (data-accessor o)]
      (or (identical? this da) (instance? IntBufferAccessor da))))
  BufferAccessor
  (toSeq [this buf offset stride]
    (if (< offset (.count this buf))
      (wrap-byte-seq int32 (* Integer/BYTES stride) (* Integer/BYTES offset) (byte-seq buf))
      (list)))
  (slice [_ buf k l]
    (slice-buffer buf (* Integer/BYTES k) (* Integer/BYTES l)))
  IntegerBufferAccessor
  (get [_ buf i]
    (.getInt buf (* Integer/BYTES i)))
  (set [_ buf i val]
    (.putInt buf (* Integer/BYTES i) val)))

(def int-accessor (->IntBufferAccessor))

(deftype LongBufferAccessor []
  DataAccessor
  (entryType [_]
    Long/TYPE)
  (entryWidth [_]
    Long/BYTES)
  (count [_ b]
    (quot (.capacity ^ByteBuffer b) Long/BYTES))
  (createDataSource [_ n]
    (direct-buffer (* Long/BYTES n)))
  (initialize [_ b]
    b)
  (initialize [this b v]
    (let [v (double v)
          strd Long/BYTES]
      (dotimes [i (.count this b)]
        (.putInt ^ByteBuffer b (* strd i) v))
      b))
  (wrapPrim [_ s]
    (wrap-long s))
  DataAccessorProvider
  (data-accessor [this]
    this)
  MemoryContext
  (compatible? [this o]
    (let [da (data-accessor o)]
      (or (identical? this da) (instance? IntBufferAccessor da))))
  BufferAccessor
  (toSeq [this buf offset stride]
    (if (< offset (.count this buf))
      (wrap-byte-seq int64 (* Long/BYTES stride) (* Long/BYTES offset) (byte-seq buf))
      (list)))
  (slice [_ buf k l]
    (slice-buffer buf (* Long/BYTES k) (* Long/BYTES l)))
  IntegerBufferAccessor
  (get [_ buf i]
    (.getLong buf (* Long/BYTES i)))
  (set [_ buf i val]
    (.putLong buf (* Long/BYTES i) val)))

(def long-accessor (->LongBufferAccessor))

;; ==================== Transfer macros and functions  =============================================

(defn matrix-equals [^RealNativeMatrix a ^RealNativeMatrix b]
  (or (identical? a b)
      (and (instance? (class a) b) (= (.matrixType ^MatrixImplementation a) (.matrixType ^MatrixImplementation b))
           (compatible? a b) (fits? a b)
           (let [nav (real-navigator a)
                 da (real-accessor a)
                 buf (.buffer a)
                 ofst (.offset a)]
             (and-layout a i j idx (= (.get da buf (+ ofst idx)) (.get nav b i j)))))))

(defmacro ^:private transfer-matrix-matrix
  ([condition source destination]
   `(do
      (if (and (<= (.mrows ~destination) (.mrows ~source)) (<= (.ncols ~destination) (.ncols ~source)))
        (if (and (compatible? ~source ~destination) (fits? ~source ~destination) ~condition)
          (copy (engine ~source) ~source ~destination)
          (let [nav# (real-navigator ~destination)
                da# (real-accessor ~destination)
                buf# (.buffer ~destination)
                ofst# (.offset ~destination)]
            (doall-layout ~destination i# j# idx# (.set da# buf# (+ ofst# idx#) (.get nav# ~source i# j#)))))
        (dragan-says-ex "There is not enough entries in the source matrix. Take appropriate submatrix of the destination.."
                        {:source (str ~source) :destination (str ~destination)}))
      ~destination))
  ([source destination]
   `(transfer-matrix-matrix true ~source ~destination)))

(defmacro ^:private transfer-seq-matrix [source destination]
  `(let [da# (real-accessor ~destination)
         buf# (.buffer ~destination)
         ofst# (.offset ~destination)]
     (doseq-layout ~destination i# j# idx# ~source e# (.set da# buf# (+ ofst# idx#) e#))
     ~destination))

(defmacro ^:private transfer-vector-matrix [source destination]
  `(let [stor# (storage ~destination)]
     (if (and (compatible? ~source ~destination) (.isGapless stor#))
       (copy (engine ~source) ~source (view-vctr ~destination))
       (let [da# (real-accessor ~destination)
             nav# (navigator ~destination)
             reg# (region ~destination)
             buf# (.buffer ~destination)
             ofst# (.offset ~destination)
             dim# (.dim ~destination)]
         (doall-layout nav# stor# reg# i# j# idx# cnt#
                       (when (< cnt# dim#)
                         (.set da# buf# (+ ofst# idx#) (.entry ~source cnt#))))
         ~destination))))

(defmacro ^:private transfer-matrix-vector [source destination]
  `(let [stor# (storage ~source)]
     (if (and (compatible? ~destination ~source) (.isGapless stor#))
       (let [src# (view-vctr ~source)]
         (copy (engine src#) src# ~destination))
       (let [da# (real-accessor ~source)
             nav# (navigator ~source)
             reg# (region ~source)
             buf# (.buffer ~source)
             ofst# (.offset ~source)
             dim# (.dim ~destination)]
         (doall-layout nav# stor# reg# i# j# idx# cnt#
                       (when (< cnt# dim#)
                         (.set ~destination cnt# (.get da# buf# (+ ofst# idx#)))))
         ~destination))))

(defmacro ^:private transfer-array-matrix [source destination]
  ` (let [da# (real-accessor ~destination)
          nav# (navigator ~destination)
          stor# (storage ~destination)
          reg# (region ~destination)
          buf# (.buffer ~destination)
          ofst# (.offset ~destination)
          len# (alength ~source)]
      (doall-layout nav# stor# reg# i# j# idx# cnt#
                    (when (< cnt# len#)
                      (.set da# buf# (+ ofst# idx#) (aget ~source cnt#))))
      ~destination))

(defmacro ^:private transfer-matrix-array [source destination]
  `(let [da# (real-accessor ~source)
         nav# (navigator ~source)
         stor# (storage ~source)
         reg# (region ~source)
         buf# (.buffer ~source)
         ofst# (.offset ~source)
         len# (alength ~destination)]
     (doall-layout nav# stor# reg# i# j# idx# cnt#
                   (when (< cnt# len#)
                     (aset ~destination cnt# (.get da# buf# (+ ofst# idx#)))))
     ~destination))

(defmacro ^:private transfer-vector-vector [source destination]
  `(do
     (if (and (compatible? ~source ~destination) (fits? ~source ~destination))
       (copy! ~source ~destination)
       (dotimes [i# (min (.dim ~source) (.dim ~destination))]
         (.set ~destination i# (.entry ~source i#))))
     ~destination))

(defmacro ^:private transfer-vector-array [source destination]
  `(let [n# (min (.dim ~source) (alength ~destination))]
     (dotimes [i# n#]
       (aset ~destination i# (.entry ~source i#)))
     ~destination))

(defmacro ^:private transfer-array-vector [source destination]
  `(let [n# (min (alength ~source) (.dim ~destination))]
     (dotimes [i# n#]
       (.set ~destination i# (aget ~source i#)))
     ~destination))

(defmacro ^:private transfer-seq-vector [source destination]
  `(let [n# (.dim ~destination)]
     (loop [i# 0 src# (seq ~source)]
       (when (and src# (< i# n#))
         (.set ~destination i# (first src#))
         (recur (inc i#) (next src#))))
     ~destination))

;; ============ Integer Vector =================================================

(deftype IntegerBlockVector [fact ^IntegerBufferAccessor da eng ^Boolean master ^ByteBuffer buf
                             ^long n ^long ofst ^long strd]
  Object
  (hashCode [x]
    (-> (hash :IntegerBlockVector) (hash-combine n) (hash-combine (nrm2 eng x))))
  (equals [x y]
    (cond
      (nil? y) false
      (identical? x y) true
      (and (instance? IntegerBlockVector y) (compatible? da y) (fits? x y))
      (loop [i 0]
        (if (< i n)
          (and (= (.entry x i) (.entry ^IntegerBlockVector y i)) (recur (inc i)))
          true))
      :default false))
  (toString [_]
    (format "#IntegerBlockVector[%s, n:%d, offset: %d, stride:%d]" (.entryType da) n ofst strd))
  Releaseable
  (release [_]
    (if master (clean-buffer buf) true))
  Seqable
  (seq [_]
    (take n (.toSeq da buf ofst strd)))
  Container
  (raw [_]
    (integer-block-vector fact n))
  (raw [_ fact]
    (create-vector fact n false))
  (zero [_]
    (integer-block-vector fact n))
  (zero [_ fact]
    (create-vector fact n true))
  (host [x]
    (let-release [res (raw x)]
      (copy eng x res)))
  (native [x]
    x)
  MemoryContext
  (compatible? [_ y]
    (compatible? da y))
  (fits? [_ y]
    (= n (.dim ^VectorSpace y)))
  EngineProvider
  (engine [_]
    nil)
  FactoryProvider
  (factory [_]
    fact)
  (native-factory [_]
    (native-factory fact))
  (index-factory [_]
    (index-factory fact))
  DataAccessorProvider
  (data-accessor [_]
    da)
  IFn$LLL
  (invokePrim [x i v]
    (.set x i v))
  IFn$LL
  (invokePrim [x i]
    (.entry x i))
  IFn$L
  (invokePrim [x]
    n)
  IFn
  (invoke [x i v]
    (.set x i v))
  (invoke [x i]
    (.entry x i))
  (invoke [x]
    n)
  IntegerChangeable
  (set [x val]
    (set-all eng val x)
    x)
  (set [x i val]
    (.set da buf (+ ofst (* strd i)) val)
    x)
  (setBoxed [x val]
    (.set x val))
  (setBoxed [x i val]
    (.set x i val))
  (alter [x f]
    (if (instance? IFn$LL f)
      (dotimes [i n]
        (.set x i (.invokePrim ^IFn$LL f (.entry x i))))
      (dotimes [i n]
        (.set x i (.invokePrim ^IFn$LLL f i (.entry x i)))))
    x)
  (alter [x i f]
    (.set x i (.invokePrim ^IFn$LL f (.entry x i))))
  IntegerNativeVector
  (buffer [_]
    buf)
  (offset [_]
    ofst)
  (stride [_]
    strd)
  (dim [_]
    n)
  (entry [_ i]
    (.get da buf (+ ofst (* strd i))))
  (boxedEntry [x i]
    (.entry x i))
  (subvector [_ k l]
    (integer-block-vector fact false buf l (+ ofst (* k strd)) strd))
  Monoid
  (id [x]
    (integer-block-vector fact 0)))

(defn integer-block-vector
  ([fact master ^ByteBuffer buf n ofst strd]
   (->IntegerBlockVector fact (data-accessor fact) (vector-engine fact) master buf n ofst strd))
  ([fact n]
   (let-release [buf (.createDataSource (data-accessor fact) n)]
     (integer-block-vector fact true buf n 0 1))))

(defmethod print-method IntegerBlockVector
  [^Vector x ^java.io.Writer w]
  (.write w (format "%s%s" (str x) (pr-str (take 100 (seq x))))))

(defmethod transfer! [IntegerBlockVector IntegerBlockVector]
  [^IntegerBlockVector source ^IntegerBlockVector destination]
  (transfer-vector-vector source destination))

(defmethod transfer! [clojure.lang.Sequential IntegerBlockVector]
  [source ^IntegerBlockVector destination]
  (transfer-seq-vector source destination))

(defmethod transfer! [(Class/forName "[D") IntegerBlockVector]
  [^doubles source ^IntegerBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [(Class/forName "[F") IntegerBlockVector]
  [^floats source ^IntegerBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [(Class/forName "[J") IntegerBlockVector]
  [^longs source ^IntegerBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [(Class/forName "[I") IntegerBlockVector]
  [^ints source ^IntegerBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [IntegerBlockVector (Class/forName "[J")]
  [^IntegerBlockVector source ^longs destination]
  (transfer-vector-array source destination))

(defmethod transfer! [IntegerBlockVector (Class/forName "[I")]
  [^IntegerBlockVector source ^ints destination]
  (transfer-vector-array source destination))

;; ============ Real Vector ====================================================

(deftype RealBlockVector [fact ^RealBufferAccessor da eng ^Boolean master ^ByteBuffer buf
                          ^long n ^long ofst ^long strd]
  Object
  (hashCode [x]
    (-> (hash :RealBlockVector) (hash-combine n) (hash-combine (nrm2 eng x))))
  (equals [x y]
    (cond
      (nil? y) false
      (identical? x y) true
      (and (instance? RealBlockVector y) (compatible? da y) (fits? x y))
      (loop [i 0]
        (if (< i n)
          (and (= (.entry x i) (.entry ^RealBlockVector y i)) (recur (inc i)))
          true))
      :default false))
  (toString [_]
    (format "#RealBlockVector[%s, n:%d, offset: %d, stride:%d]" (.entryType da) n ofst strd))
  Releaseable
  (release [_]
    (if master (clean-buffer buf) true))
  Seqable
  (seq [_]
    (take n (.toSeq da buf ofst strd)))
  Container
  (raw [_]
    (real-block-vector fact n))
  (raw [_ fact]
    (create-vector fact n false))
  (zero [_]
    (real-block-vector fact n))
  (zero [_ fact]
    (create-vector fact n true))
  (host [x]
    (let-release [res (raw x)]
      (copy eng x res)))
  (native [x]
    x)
  DenseContainer
  (view-vctr [_]
    (real-block-vector fact false buf n ofst strd))
  (view-vctr [_ stride-mult]
    (real-block-vector fact false buf (ceil (/ n (long stride-mult))) ofst (* (long stride-mult) strd)))
  (view-ge [_]
    (real-ge-matrix fact false buf n 1 ofst (layout-navigator true) (full-storage true n 1) (ge-region n 1)))
  (view-ge [x stride-mult]
    (view-ge (view-ge x) stride-mult))
  (view-tr [x uplo diag]
    (view-tr (view-ge x) uplo diag))
  (view-sy [x uplo]
    (view-sy (view-ge x) uplo))
  MemoryContext
  (compatible? [_ y]
    (compatible? da y))
  (fits? [_ y]
    (= n (.dim ^VectorSpace y)))
  EngineProvider
  (engine [_]
    eng)
  FactoryProvider
  (factory [_]
    fact)
  (native-factory [_]
    (native-factory fact))
  (index-factory [_]
    (index-factory fact))
  DataAccessorProvider
  (data-accessor [_]
    da)
  IFn$LDD
  (invokePrim [x i v]
    (.set x i v))
  IFn$LD
  (invokePrim [x i]
    (entry x i))
  IFn$L
  (invokePrim [x]
    n)
  IFn
  (invoke [x i v]
    (.set x i v))
  (invoke [x i]
    (entry x i))
  (invoke [x]
    n)
  RealChangeable
  (set [x val]
    (if (not (Double/isNaN val))
      (set-all eng val x)
      (dotimes [i n]
        (.set x i val)))
    x)
  (set [x i val]
    (.set da buf (+ ofst (* strd i)) val)
    x)
  (setBoxed [x val]
    (.set x val))
  (setBoxed [x i val]
    (.set x i val))
  (alter [x f]
    (if (instance? IFn$DD f)
      (dotimes [i n]
        (.set x i (.invokePrim ^IFn$DD f (.entry x i))))
      (dotimes [i n]
        (.set x i (.invokePrim ^IFn$LDD f i (.entry x i)))))
    x)
  (alter [x i f]
    (.set x i (.invokePrim ^IFn$DD f (.entry x i))))
  RealNativeVector
  (buffer [_]
    buf)
  (offset [_]
    ofst)
  (stride [_]
    strd)
  (dim [_]
    n)
  (entry [_ i]
    (.get da buf (+ ofst (* strd i))))
  (boxedEntry [x i]
    (.entry x i))
  (subvector [_ k l]
    (real-block-vector fact false buf l (+ ofst (* k strd)) strd))
  Monoid
  (id [x]
    (real-block-vector fact 0))
  PseudoFunctor
  (fmap! [x f]
    (vector-fmap* ^IFn$DD f x))
  (fmap! [x f y]
    (vector-fmap* ^IFn$DDD f x ^RealVector y))
  (fmap! [ x f y z]
    (vector-fmap* ^IFn$DDDD f x ^RealVector y ^RealVector z))
  (fmap! [x f y z v]
    (vector-fmap* ^IFn$DDDDD f x ^RealVector y ^RealVector z ^RealVector v))
  (fmap! [x f y z v ws]
    (vector-fmap* f x y z v ws))
  Foldable
  (fold [x]
    (sum (engine x) x))
  (fold [x f init]
    (vector-reduce f init x))
  (fold [x f init y]
    (vector-reduce f init x y))
  (fold [x f init y z]
    (vector-reduce f init x y z))
  (fold [x f init y z v]
    (vector-reduce f init x y z v))
  (fold [x f init y z v ws]
    (vector-reduce* nil i j nil nil nil nil nil nil nil))
  (foldmap [x g]
    (loop [i 0 acc 0.0]
      (if (< i n)
        (recur (inc i) (+ acc (.invokePrim ^IFn$DD g (.entry x i))))
        acc)))
  (foldmap[x g f init]
    (vector-map-reduce f init g x))
  (foldmap[x g f init y]
    (vector-map-reduce f init g x y))
  (foldmap[x g f init y z]
    (vector-map-reduce f init g x y z))
  (foldmap[x g f init y z v]
    (vector-map-reduce f init g x y z v))
  (foldmap [_ _ _ _ _ _ _ _]
    (vector-map-reduce* nil i j nil nil  nil nil nil nil nil nil)))

(defn real-block-vector
  ([fact master ^ByteBuffer buf n ofst strd]
   (let [da (data-accessor fact)]
     (if (and (<= 0 n (.count da buf)))
       (->RealBlockVector fact da (vector-engine fact) master buf n ofst strd)
       (throw (ex-info "Insufficient buffer size." {:n n :buffer-size (.count da buf)})))))
  ([fact n]
   (let-release [buf (.createDataSource (data-accessor fact) n)]
     (real-block-vector fact true buf n 0 1))))

(extend RealBlockVector
  Functor
  {:fmap copy-fmap}
  Applicative
  {:pure vector-pure}
  Magma
  {:op (constantly vector-op)})

(defmethod print-method RealBlockVector [^Vector x ^java.io.Writer w]
  (.write w (str x))
  (print-vector w x))

(defmethod transfer! [RealBlockVector RealBlockVector]
  [^RealBlockVector source ^RealBlockVector destination]
  (transfer-vector-vector source destination))

(defmethod transfer! [IntegerBlockVector RealBlockVector]
  [^IntegerBlockVector source ^RealBlockVector destination]
  (transfer-vector-vector source destination))

(defmethod transfer! [RealBlockVector IntegerBlockVector]
  [^RealBlockVector source ^IntegerBlockVector destination]
  (transfer-vector-vector source destination))

(defmethod transfer! [clojure.lang.Sequential RealBlockVector]
  [source ^RealBlockVector destination]
  (transfer-seq-vector source destination))

(defmethod transfer! [(Class/forName "[D") RealBlockVector]
  [^doubles source ^RealBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [(Class/forName "[F") RealBlockVector]
  [^floats source ^RealBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [(Class/forName "[J") RealBlockVector]
  [^longs source ^RealBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [(Class/forName "[I") RealBlockVector]
  [^ints source ^RealBlockVector destination]
  (transfer-array-vector source destination))

(defmethod transfer! [RealBlockVector (Class/forName "[D")]
  [^RealBlockVector source ^doubles destination]
  (transfer-vector-array source destination))

(defmethod transfer! [RealBlockVector (Class/forName "[F")]
  [^RealBlockVector source ^floats destination]
  (transfer-vector-array source destination))

;; =================== Real Matrix =============================================

(deftype RealGEMatrix [^LayoutNavigator nav ^DenseStorage stor ^Region reg
                       fact ^RealBufferAccessor da eng ^Boolean master
                       ^ByteBuffer buf ^long m ^long n ^long ofst]
  Object
  (hashCode [a]
    (-> (hash :RealGEMatrix) (hash-combine m) (hash-combine n) (hash-combine (nrm2 eng (.dia a)))))
  (equals [a b]
    (matrix-equals a b))
  (toString [a]
    (format "#RealGEMatrix[%s, mxn:%dx%d, layout%s, offset:%d]"
            (.entryType da) m n (dec-property (.layout nav)) ofst))
  Releaseable
  (release [_]
    (if master (clean-buffer buf) true))
  GEMatrix
  (matrixType [_]
    :ge)
  (isTriangular [_]
    false)
  EngineProvider
  (engine [_]
    eng)
  FactoryProvider
  (factory [_]
    fact)
  (native-factory [_]
    (native-factory fact))
  (index-factory [_]
    (index-factory fact))
  DataAccessorProvider
  (data-accessor [_]
    da)
  Navigable
  (navigator [_]
    nav)
  (storage [_]
    stor)
  (region [_]
    reg)
  Container
  (raw [_]
    (real-ge-matrix fact m n nav stor reg))
  (raw [_ fact]
    (create-ge fact m n (.isColumnMajor nav) false))
  (zero [a]
    (raw a))
  (zero [_ fact]
    (create-ge fact m n (.isColumnMajor nav) true))
  (host [a]
    (let-release [res (raw a)]
      (copy eng a res)))
  (native [a]
    a)
  DenseContainer
  (view-vctr [a]
    (if (.isGapless stor)
      (real-block-vector fact false buf (.dim a) ofst 1)
      (throw (ex-info "Strided GE matrix cannot be viewed as a dense vector." {:a (str a)}))))
  (view-vctr [a stride-mult]
    (view-vctr (view-vctr a) stride-mult))
  (view-ge [a]
    a)
  (view-ge [_ stride-mult]
    (let [shrinked (ceil (/ (.fd stor) (long stride-mult)))
          column-major (.isColumnMajor nav)
          [m n] (if column-major [m shrinked] [shrinked n])]
      (real-ge-matrix fact false buf m n ofst nav
                      (full-storage column-major m n (* (long stride-mult) (.ld ^FullStorage stor)))
                      (ge-region m n))))
  (view-tr [_ lower? diag-unit?]
    (let [n (min m n)]
      (real-uplo-matrix fact false buf n ofst nav (full-storage n n (.ld ^FullStorage stor))
                        (band-region n lower? diag-unit?) :tr (real-default :tr diag-unit?)
                        (tr-engine fact))))
  (view-sy [_ lower?]
    (let [n (min m n)]
      (real-uplo-matrix fact false buf n ofst nav (full-storage n n (.ld ^FullStorage stor))
                        (band-region n lower?) :sy sy-default (sy-engine fact))))
  MemoryContext
  (compatible? [_ b]
    (compatible? da b))
  (fits? [_ b]
    (and (instance? GEMatrix b)) (= reg (region b)))
  (fits-navigation? [_ b]
    (= nav (navigator b)))
  Monoid
  (id [a]
    (real-ge-matrix fact 0 0 (.isColumnMajor nav)))
  Seqable
  (seq [a]
    (map #(seq (.stripe nav a %)) (range 0 (.fd stor))))
  IFn$LLDD
  (invokePrim [a i j v]
    (entry! a i j v))
  IFn$LLD
  (invokePrim [a i j]
    (entry a i j))
  IFn
  (invoke [a i j v]
    (entry! a i j v))
  (invoke [a i j]
    (entry a i j))
  (invoke [a]
    (.fd stor))
  IFn$L
  (invokePrim [a]
    (.fd stor))
  RealChangeable
  (isAllowed [a i j];;TODO rename to isAccessible
    true)
  (set [a val]
    (if (not (Double/isNaN val))
      (set-all eng val a)
      (doall-layout nav stor reg i j idx (.set da buf (+ ofst idx) val)))
    a)
  (set [a i j val]
    (.set da buf (+ ofst (.index nav stor i j)) val)
    a)
  (setBoxed [a val]
    (.set a val))
  (setBoxed [a i j val]
    (.set a i j val))
  (alter [a f]
    (if (instance? IFn$DD f)
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrim ^IFn$DD f (.get da buf (+ ofst idx)))))
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrimitive ^RealLayoutNavigator nav f i j
                                                                (.get da buf (+ ofst idx))))))
    a)
  (alter [a i j f]
    (let [idx (+ ofst (.index nav stor i j))]
      (.set da buf idx (.invokePrim ^IFn$DD f (.get da buf idx)))
      a))
  RealNativeMatrix
  (buffer [_]
    buf)
  (offset [_]
    ofst)
  (stride [_]
    (.ld ^FullStorage stor))
  (dim [_]
    (* m n))
  (mrows [_]
    m)
  (ncols [_]
    n)
  (entry [a i j]
    (.get da buf (+ ofst (.index nav stor i j))))
  (boxedEntry [a i j]
    (.entry a i j))
  (row [a i]
    (real-block-vector fact false buf n (+ ofst (.index nav stor i 0))
                       (if (.isRowMajor nav) 1 (.ld ^FullStorage stor))))
  (rows [a]
    (dense-rows a))
  (col [a j]
    (real-block-vector fact false buf m (+ ofst (.index nav stor 0 j))
                       (if (.isColumnMajor nav) 1 (.ld ^FullStorage stor))))
  (cols [a]
    (dense-cols a))
  (dia [a]
    (real-block-vector fact false buf (min m n) ofst (inc (.ld ^FullStorage stor))))
  (dia [a k]
    (if (< 0 k)
      (real-block-vector fact false buf (min m (- n k)) (+ ofst (.index nav stor (- k) 0))
                         (inc (.ld ^FullStorage stor)))
      (real-block-vector fact false buf (min (+ m k) n) (+ ofst (.index nav stor 0 k))
                         (inc (.ld ^FullStorage stor)))))
  (dias [a]
    (dense-dias a))
  (submatrix [a i j k l]
    (real-ge-matrix fact false buf k l (+ ofst (.index nav stor i j))
                    nav (full-storage (.isColumnMajor nav) k l (.ld ^FullStorage stor)) (ge-region k l)))
  (transpose [a]
    (real-ge-matrix fact false buf n m ofst (flip nav) stor (flip reg)))
  TRF
  (trtrs [_ _]
    (require-trf))
  (trtri! [_]
    (require-trf))
  (trtri [_]
    (require-trf))
  (trcon [_ _ _]
    (require-trf))
  (trcon [_ _]
    (require-trf))
  (trdet [_]
    (require-trf)))

(defn real-ge-matrix
  ([fact master buf m n ofst nav stor reg]
   (->RealGEMatrix nav stor reg fact (data-accessor fact) (ge-engine fact) master buf m n ofst))
  ([fact m n nav ^DenseStorage stor reg]
   (let-release [buf (.createDataSource (data-accessor fact) (.capacity stor))]
     (real-ge-matrix fact true buf m n 0 nav stor reg)))
  ([fact ^long m ^long n column?]
   (real-ge-matrix fact m n (layout-navigator column?) (full-storage column? m n) (ge-region m n)))
  ([fact ^long m ^long n]
   (real-ge-matrix fact m n true)))

(extend RealGEMatrix
  Functor
  {:fmap copy-fmap}
  PseudoFunctor
  {:fmap! matrix-fmap!}
  Applicative
  {:pure matrix-pure}
  Foldable
  {:fold matrix-fold
   :foldmap matrix-foldmap}
  Magma
  {:op (constantly matrix-op)})

(defmethod print-method RealGEMatrix [a ^java.io.Writer w]
  (.write w (str a))
  (print-ge w a))

;; =================== Real Uplo Matrix ==================================

(deftype RealUploMatrix [^LayoutNavigator nav ^DenseStorage stor ^Region reg ^RealDefault default
                         fact ^RealBufferAccessor da eng matrix-type
                         ^Boolean master ^ByteBuffer buf ^long n ^long ofst]
  Object
  (hashCode [a]
    (-> (hash :RealUploMatrix) (hash-combine n) (hash-combine (nrm2 eng (.dia a)))))
  (equals [a b]
    (matrix-equals a b))
  (toString [a]
    (format "#RealUploMatrix[%s, type%s, mxn:%dx%d, layout%s, offset:%d]"
            (.entryType da) matrix-type n n (dec-property (.layout nav)) ofst))
  Releaseable
  (release [_]
    (if master (clean-buffer buf) true))
  UploMatrix
  (matrixType [_]
    matrix-type)
  (isTriangular [_]
    (= :tr matrix-type))
  EngineProvider
  (engine [_]
    eng)
  FactoryProvider
  (factory [_]
    fact)
  (native-factory [_]
    (native-factory fact))
  (index-factory [_]
    (index-factory fact))
  DataAccessorProvider
  (data-accessor [_]
    da)
  Navigable
  (navigator [_]
    nav)
  (storage [_]
    stor)
  (region [_]
    reg)
  Container
  (raw [_]
    (real-uplo-matrix fact n nav stor reg matrix-type default eng))
  (raw [_ fact]
    (create-uplo fact n matrix-type (.isColumnMajor nav) (.isLower reg) (.isDiagUnit reg) false))
  (zero [a]
    (raw a))
  (zero [_ fact]
    (create-uplo fact n matrix-type (.isColumnMajor nav) (.isLower reg) (.isDiagUnit reg) false))
  (host [a]
    (let-release [res (raw a)]
      (copy eng a res)))
  (native [a]
    a)
  DenseContainer
  (view-vctr [a]
    (view-vctr (view-ge a)))
  (view-vctr [a stride-mult]
    (view-vctr (view-ge a) stride-mult))
  (view-ge [_]
    (real-ge-matrix fact false buf n n ofst nav stor (ge-region n n) ))
  (view-ge [a stride-mult]
    (view-ge (view-ge a) stride-mult))
  (view-tr [_ lower? diag-unit?]
    (real-uplo-matrix fact false buf n ofst nav stor (band-region n lower? diag-unit?)
                      :tr (real-default :tr diag-unit?) (tr-engine fact)))
  (view-sy [_ lower?]
    (real-uplo-matrix fact false buf n ofst nav stor (band-region n lower?)
                      :sy sy-default (sy-engine fact)))
  MemoryContext
  (compatible? [_ b]
    (compatible? da b))
  (fits? [_ b]
    (and (instance? UploMatrix b) (= reg (region b))))
  (fits-navigation? [_ b]
    (and (= nav (navigator b))
         (or (instance? GEMatrix b) (= reg (region b)))))
  Monoid
  (id [a]
    (real-uplo-matrix fact 0 (.isColumnMajor nav) matrix-type))
  Seqable
  (seq [a]
    (map #(seq (.stripe nav a %)) (range 0 n)))
  IFn$LLDD
  (invokePrim [x i j v]
    (entry! x i j v))
  IFn$LLD
  (invokePrim [a i j]
    (entry a i j))
  IFn
  (invoke [x i j v]
    (entry! x i j v))
  (invoke [a i j]
    (entry a i j))
  (invoke [a]
    n)
  IFn$L
  (invokePrim [a]
    n)
  RealChangeable
  (isAllowed [a i j]
    (.accessible reg i j))
  (set [a val]
    (if (not (Double/isNaN val))
      (set-all eng val a)
      (doall-layout nav stor reg i j idx (.set da buf (+ ofst idx) val)))
    a)
  (set [a i j val]
    (.set da buf (+ ofst (.index nav stor i j)) val)
    a)
  (setBoxed [a val]
    (.set a val))
  (setBoxed [a i j val]
    (.set a i j val))
  (alter [a f]
    (if (instance? IFn$DD f)
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrim ^IFn$DD f (.get da buf (+ ofst idx)))))
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrimitive ^RealLayoutNavigator nav f i j
                                                                (.get da buf (+ ofst idx))))))
    a)
  (alter [a i j f]
    (let [idx (+ ofst (.index nav stor i j))]
      (.set da buf idx (.invokePrim ^IFn$DD f (.get da buf idx)))
      a))
  RealNativeMatrix
  (buffer [_]
    buf)
  (offset [_]
    ofst)
  (stride [_]
    (.ld ^FullStorage stor))
  (dim [_]
    (* n n))
  (mrows [_]
    n)
  (ncols [_]
    n)
  (entry [a i j]
    (if (.accessible reg i j)
      (.get da buf (+ ofst (.index nav stor i j)))
      (.entry default stor da buf ofst i j)))
  (boxedEntry [a i j]
    (.entry a i j))
  (row [a i]
    (let [start (.rowStart reg i)]
      (real-block-vector fact false buf (- (.rowEnd reg i) start) (+ ofst (.index nav stor i start))
                         (if (.isRowMajor nav) 1 (.ld ^FullStorage stor)))))
  (rows [a]
    (dense-rows a))
  (col [a j]
    (let [start (.colStart reg j)]
      (real-block-vector fact false buf (- (.colEnd reg j) start) (+ ofst (.index nav stor start j))
                         (if (.isColumnMajor nav) 1 (.ld ^FullStorage stor)))))
  (cols [a]
    (dense-cols a))
  (dia [a]
    (real-block-vector fact false buf n ofst (inc (.ld ^FullStorage stor))))
  (dia [a k]
    (if (<= (- (.kl reg)) k (.ku reg))
      (if (< 0 k)
        (real-block-vector fact false buf (- n k) (+ ofst (.index nav stor (- k) 0))
                           (inc (.ld ^FullStorage stor)))
        (real-block-vector fact false buf (+ n k) (+ ofst (.index nav stor 0 k))
                           (inc (.ld ^FullStorage stor))))
      (real-block-vector fact false buf 0 ofst 1)))
  (dias [a]
    (region-dias a))
  (submatrix [a i j k l]
    (if (and (= i j) (= k l))
      (real-uplo-matrix fact false buf k (+ ofst (.index nav stor i j)) nav
                        (full-storage (.isColumnMajor nav) k k (.ld ^FullStorage stor))
                        (band-region k (.isLower reg) (.isDiagUnit reg)) matrix-type default eng)
      (dragan-says-ex "You cannot create a non-uplo submatrix of a uplo (TR or SY) matrix. Take a view-ge."
                      {:a (str a) :i i :j j :k k :l l})))
  (transpose [a]
    (real-uplo-matrix fact false buf n ofst (flip nav) stor (flip reg) matrix-type default eng))
  TRF
  (trtrs [a b]
    (trs eng a b))
  (trtri! [a]
    (tri eng a))
  (trtri [a]
    (let-release [res (raw a)]
      (tri eng (copy eng a res))))
  (trcon [a _ nrm1?]
    (con eng a nrm1?))
  (trcon [a nrm1?]
    (con eng a nrm1?))
  (trdet [a]
    (if (.isDiagUnit reg) 1.0 (fold (.dia a) f* 1.0))))

(extend RealUploMatrix
  Functor
  {:fmap copy-fmap}
  PseudoFunctor
  {:fmap! matrix-fmap!}
  Applicative
  {:pure matrix-pure}
  Foldable
  {:fold matrix-fold
   :foldmap matrix-foldmap}
  Magma
  {:op (constantly matrix-op)})

(defn real-uplo-matrix
  ([fact master buf n ofst nav stor reg matrix-type default engine]
   (->RealUploMatrix nav stor reg default fact (data-accessor fact) engine matrix-type
                     master buf n ofst))
  ([fact n nav ^DenseStorage stor reg matrix-type default engine]
   (let-release [buf (.createDataSource (data-accessor fact) (.capacity stor))]
     (real-uplo-matrix fact true buf n 0 nav stor reg matrix-type default engine)))
  ([fact n column? lower? diag-unit? matrix-type]
   (real-uplo-matrix fact n (layout-navigator column?) (full-storage column? n n)
                     (band-region n lower? diag-unit?) matrix-type (real-default matrix-type diag-unit?)
                     (case matrix-type
                       :tr (tr-engine fact)
                       :sy (sy-engine fact)
                       (dragan-says-ex (format "%s is not a valid UPLO matrix type. Please send me a bug report."
                                               matrix-type)
                                       {:type matrix-type}))))
  ([fact n column? lower? diag-unit?]
   (real-uplo-matrix fact n (layout-navigator column?) (full-storage column? n n)
                     (band-region n lower? diag-unit?) :tr (real-default :tr diag-unit?) (tr-engine fact)))
  ([fact n column? lower?]
   (real-uplo-matrix fact n (layout-navigator column?) (full-storage column? n n)
                     (band-region n lower?) :sy (real-default :sy) (sy-engine fact))))

(defmethod print-method RealUploMatrix [a ^java.io.Writer w]
  (.write w (str a))
  (print-uplo w a "*"))

;; ================= Banded Matrix ==============================================================

(deftype RealBandedMatrix [^LayoutNavigator nav ^DenseStorage stor ^Region reg ^RealDefault default
                           fact ^RealBufferAccessor da eng matrix-type
                           ^Boolean master ^ByteBuffer buf ^long m ^long n ^long ofst]
  Object
  (hashCode [a]
    (-> (hash :RealBandedMatrix) (hash-combine matrix-type) (hash-combine m) (hash-combine n)
        (hash-combine (nrm2 eng (.dia a)))))
  (equals [a b]
    (matrix-equals a b))
  (toString [a]
    (format "#RealBandedMatrix[%s, type%s, mxn:%dx%d, layout%s, offset:%d]"
            (.entryType da) matrix-type m n (dec-property (.layout nav)) ofst))
  Releaseable
  (release [_]
    (if master (clean-buffer buf) true))
  BandedMatrix
  (matrixType [_]
    matrix-type)
  (isTriangular [_]
    (= :tb matrix-type))
  EngineProvider
  (engine [_]
    eng)
  FactoryProvider
  (factory [_]
    fact)
  (native-factory [_]
    (native-factory fact))
  (index-factory [_]
    (index-factory fact))
  DataAccessorProvider
  (data-accessor [_]
    da)
  Navigable
  (navigator [_]
    nav)
  (storage [_]
    stor)
  (region [_]
    reg)
  Container
  (raw [_]
    (real-banded-matrix fact m n nav stor reg matrix-type default eng))
  (raw [_ fact]
    (create-banded fact m n (.kl reg) (.ku reg) matrix-type (.isColumnMajor nav) false))
  (zero [a]
    (raw a))
  (zero [_ fact]
    (create-banded fact m n (.kl reg) (.ku reg) matrix-type (.isColumnMajor nav) true))
  (host [a]
    (let-release [res (raw a)]
      (copy eng a res)))
  (native [a]
    a)
  DenseContainer
  (view-vctr [a]
    (view-vctr (view-ge a)))
  (view-vctr [a stride-mult]
    (view-vctr (view-ge a) stride-mult))
  (view-ge [_]
    (let [m (if (.isColumnMajor nav) (.sd ^FullStorage stor) (.fd stor))
          n (if (.isColumnMajor nav) (.fd stor) (.sd ^FullStorage stor))]
      (real-ge-matrix fact false buf m n ofst nav
                      (full-storage (.isColumnMajor nav) m n (.ld ^FullStorage stor))
                      (ge-region m n))))
  MemoryContext
  (compatible? [_ b]
    (compatible? da b))
  (fits? [_ b]
    (and (instance? BandedMatrix b) (= reg (region b))))
  Monoid
  (id [a]
    (real-banded-matrix fact 0 (.isColumnMajor nav) matrix-type))
  Seqable
  (seq [a]
    (map seq (.dias a)))
  IFn$LLDD
  (invokePrim [x i j v]
    (entry! x i j v))
  IFn$LLD
  (invokePrim [a i j]
    (entry a i j))
  IFn
  (invoke [x i j v]
    (entry! x i j v))
  (invoke [a i j]
    (entry a i j))
  (invoke [a]
    (.fd stor))
  IFn$L
  (invokePrim [a]
    (.fd stor))
  RealChangeable
  (isAllowed [a i j]
    (.accessible reg i j))
  (set [a val]
    (if (not (Double/isNaN val))
      (set-all eng val a)
      (doall-layout nav stor reg i j idx (.set da buf (+ ofst idx) val)))
    a)
  (set [a i j val]
    (.set da buf (+ ofst (.index nav stor i j)) val)
    a)
  (setBoxed [a val]
    (.set a val))
  (setBoxed [a i j val]
    (.set a i j val))
  (alter [a f]
    (if (instance? IFn$DD f)
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrim ^IFn$DD f (.get da buf (+ ofst idx)))))
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrimitive ^RealLayoutNavigator nav f i j
                                                                (.get da buf (+ ofst idx))))))
    a)
  (alter [a i j f]
    (let [idx (+ ofst (.index nav stor i j))]
      (.set da buf idx (.invokePrim ^IFn$DD f (.get da buf idx)))
      a))
  RealNativeMatrix
  (buffer [_]
    buf)
  (offset [_]
    ofst)
  (stride [_]
    (.ld ^FullStorage stor))
  (dim [_]
    (* m n))
  (mrows [_]
    m)
  (ncols [_]
    n)
  (entry [a i j]
    (if (.accessible reg i j)
      (.get da buf (+ ofst (.index nav stor i j)))
      (.entry default stor da buf ofst i j)))
  (boxedEntry [a i j]
    (.entry a i j))
  (row [a i]
    (let [start (.rowStart reg i)]
      (real-block-vector fact false buf (- (.rowEnd reg i) start) (+ ofst (.index nav stor i start))
                         (if (.isRowMajor nav) 1 (dec (.ld ^FullStorage stor))))))
  (rows [a]
    (region-rows a))
  (col [a j]
    (let [start (.colStart reg j)]
      (real-block-vector fact false buf (- (.colEnd reg j) start) (+ ofst (.index nav stor start j))
                         (if (.isColumnMajor nav) 1 (dec (.ld ^FullStorage stor))))))
  (cols [a]
    (region-cols a))
  (dia [a]
    (real-block-vector fact false buf (min m n) (+ ofst (.index nav stor 0 0)) (.ld ^FullStorage stor)))
  (dia [a k]
    (if (<= (- (.kl reg)) k (.ku reg))
      (if (< 0 k)
        (real-block-vector fact false buf (min m (- n k)) (+ ofst (.index nav stor 0 k)) (.ld ^FullStorage stor))
        (real-block-vector fact false buf (min (+ m k) n) (+ ofst (.index nav stor (- k) 0)) (.ld ^FullStorage stor)))
      (real-block-vector fact false buf 0 ofst 1)))
  (dias [a]
    (region-dias a))
  (submatrix [a i j k l]
    (if (= i j)
      (let [kl (min (.kl reg) (dec k))
            ku (min (.ku reg) (dec l))]
        (real-banded-matrix fact false buf k l (- (+ ofst (.index nav stor i j)) (inc kl))
                            nav (band-storage (.isColumnMajor nav) k l (.ld ^FullStorage stor) kl ku)
                            (band-region k l kl ku) matrix-type default eng))
      (dragan-says-ex "You cannot create a submatrix of a banded (GB, TB, or SB) matrix outside its region. No way around that."
                      {:a (str a) :i i :j j :k k :l l})))
  (transpose [a]
    (real-banded-matrix fact false buf n ofst (flip nav) stor (flip reg) matrix-type default eng)))

(extend RealBandedMatrix
  Functor
  {:fmap copy-fmap}
  PseudoFunctor
  {:fmap! matrix-fmap!}
  Applicative
  {:pure matrix-pure}
  Foldable
  {:fold matrix-fold
   :foldmap matrix-foldmap}
  Magma
  {:op (constantly matrix-op)})

(defn real-banded-matrix
  ([fact master buf m n ofst nav stor reg matrix-type default engine]
   (->RealBandedMatrix nav stor reg default fact (data-accessor fact) engine matrix-type
                       master buf m n ofst))
  ([fact m n nav ^DenseStorage stor reg matrix-type default engine]
   (let-release [buf (.createDataSource (data-accessor fact) (.capacity stor))]
     (real-banded-matrix fact true buf m n 0 nav stor reg matrix-type default engine)))
  ([fact m n kl ku column? matrix-type]
   (real-banded-matrix fact m n (layout-navigator column?) (band-storage column? m n kl ku)
                       (band-region m n kl ku) matrix-type (real-default matrix-type)
                       (case matrix-type
                         :gb (gb-engine fact)
                         :tb (tb-engine fact)
                         :sb (sb-engine fact)
                         (dragan-says-ex (format "%s is not a valid banded matrix type. Please send me a bug report."
                                                 matrix-type)
                                         {:type matrix-type}))))
  ([fact m n kl ku column?]
   (real-banded-matrix fact m n (layout-navigator column?) (band-storage column? m n kl ku)
                       (band-region m n kl ku) :gb zero-default (gb-engine fact)))
  ([fact n column? lower? diag-unit?]
   (real-banded-matrix fact n n (layout-navigator column?) (band-storage column? n lower? diag-unit?)
                       (band-region n lower? diag-unit?) :tb (real-default :tb diag-unit?) (tb-engine fact)))
  ([fact n column? lower?]
   (real-banded-matrix fact n n (layout-navigator column?) (band-storage column? n lower? false)
                       (band-region n lower?) :sb sb-default (sb-engine fact))))

(defmethod print-method RealBandedMatrix [a ^java.io.Writer w]
  (.write w (str a))
  (print-banded w a))

;; =================== Real Packed Matrix ==================================

(deftype RealPackedMatrix [^LayoutNavigator nav ^DenseStorage stor ^Region reg ^RealDefault default
                           fact ^RealBufferAccessor da eng matrix-type
                           ^Boolean master ^ByteBuffer buf ^long n ^long ofst]
  Object
  (hashCode [a]
    (-> (hash :RealPackedMatrix) (hash-combine matrix-type) (hash-combine n)
        (hash-combine (nrm2 eng a))))
  (equals [a b]
    (matrix-equals a b))
  (toString [a]
    (format "#RealPackedMatrix[%s, type%s, mxn:%dx%d, layout%s, offset:%d]"
            (.entryType da) matrix-type n n (dec-property (.layout nav)) ofst))
  Releaseable
  (release [_]
    (if master (clean-buffer buf) true))
  PackedMatrix
  (matrixType [_]
    matrix-type)
  (isTriangular [_]
    (= :tp matrix-type))
  EngineProvider
  (engine [_]
    eng)
  FactoryProvider
  (factory [_]
    fact)
  (native-factory [_]
    (native-factory fact))
  (index-factory [_]
    (index-factory fact))
  DataAccessorProvider
  (data-accessor [_]
    da)
  Navigable
  (navigator [_]
    nav)
  (storage [_]
    stor)
  (region [_]
    reg)
  Container
  (raw [_]
    (real-packed-matrix fact n nav stor reg matrix-type default eng))
  (raw [_ fact]
    (create-packed fact n matrix-type (.isColumnMajor nav) (.isLower reg) (.isDiagUnit reg) false))
  (zero [a]
    (raw a))
  (zero [_ fact]
    (create-packed fact n matrix-type (.isColumnMajor nav) (.isLower reg) (.isDiagUnit reg) true))
  (host [a]
    (let-release [res (raw a)]
      (copy eng a res)))
  (native [a]
    a)
  MemoryContext
  (compatible? [_ b]
    (compatible? da b))
  (fits? [_ b]
    (and (instance? PackedMatrix b) (= reg (region b))))
  Monoid
  (id [a]
    (real-packed-matrix fact 0 (.isColumnMajor nav) (.isLower reg) (.isDiagUnit reg) matrix-type))
  Seqable
  (seq [a]
    (map #(seq (.stripe nav a %)) (range 0 n)))
  IFn$LLDD
  (invokePrim [x i j v]
    (entry! x i j v))
  IFn$LLD
  (invokePrim [a i j]
    (entry a i j))
  IFn
  (invoke [x i j v]
    (entry! x i j v))
  (invoke [a i j]
    (entry a i j))
  (invoke [a]
    n)
  IFn$L
  (invokePrim [a]
    n)
  RealChangeable
  (isAllowed [a i j]
    (.accessible reg i j))
  (set [a val]
    (if (not (Double/isNaN val))
      (set-all eng val a)
      (doall-layout nav stor reg i j idx (.set da buf (+ ofst idx) val)))
    a)
  (set [a i j val]
    (.set da buf (+ ofst (.index nav stor i j)) val)
    a)
  (setBoxed [a val]
    (.set a val))
  (setBoxed [a i j val]
    (.set a i j val))
  (alter [a f]
    (if (instance? IFn$DD f)
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrim ^IFn$DD f (.get da buf (+ ofst idx)))))
      (doall-layout nav stor reg i j idx
                    (.set da buf (+ ofst idx) (.invokePrimitive ^RealLayoutNavigator nav f i j
                                                                (.get da buf (+ ofst idx))))))
    a)
  (alter [a i j f]
    (let [idx (+ ofst (.index nav stor i j))]
      (.set da buf idx (.invokePrim ^IFn$DD f (.get da buf idx))))
    a)
  RealNativeMatrix
  (buffer [_]
    buf)
  (offset [_]
    ofst)
  (stride [_]
    (.ld ^FullStorage stor))
  (dim [_]
    (* n n))
  (mrows [_]
    n)
  (ncols [_]
    n)
  (entry [a i j]
    (if (.accessible reg i j)
      (.get da buf (+ ofst (.index nav stor i j)))
      (.entry default stor da buf ofst i j)))
  (boxedEntry [a i j]
    (.entry a i j))
  (row [a i]
    (if (.isRowMajor nav)
      (let [j (.rowStart reg i)]
        (real-block-vector fact false buf (- (.rowEnd reg i) j) (+ ofst (.index nav stor i j)) 1))
      (dragan-says-ex "You have to unpack column-major packed matrix to access its rows."
                      {:a a :layout :column})))
  (rows [a]
    (dense-rows a))
  (col [a j]
    (if (.isColumnMajor nav)
      (let [i (.colStart reg j)]
        (real-block-vector fact false buf (- (.colEnd reg j) i) (+ ofst (.index nav stor i j)) 1))
      (dragan-says-ex "You have to unpack row-major packed matrix to access its columns."
                      {:a a :layout :row})))
  (cols [a]
    (dense-cols a))
  (dia [a]
    (dragan-says-ex "You have to unpack a packed matrix to access its diagonals." {:a a}))
  (dia [a k]
    (dragan-says-ex "You have to unpack a packed matrix to access its diagonals." {:a a}))
  (dias [a]
    (dragan-says-ex "You have to unpack a packed matrix to access its diagonals." {:a a}))
  (submatrix [a i j k l]
    (dragan-says-ex "You have to unpack a packed matrix to access its submatrices." {:a a}))
  (transpose [a]
    (real-packed-matrix fact false buf n ofst (.isRowMajor nav) (.isUpper reg) (.isDiagUnit reg) matrix-type))
  ;; TODO LU is different a bit. It's probably LDL/UDU
  )

(extend RealPackedMatrix
  Functor
  {:fmap copy-fmap}
  PseudoFunctor
  {:fmap! matrix-fmap!}
  Applicative
  {:pure matrix-pure}
  Foldable
  {:fold matrix-fold
   :foldmap matrix-foldmap}
  Magma
  {:op (constantly matrix-op)})

(defn real-packed-matrix
  ([fact master buf n ofst nav stor reg matrix-type default engine]
   (->RealPackedMatrix nav stor reg default fact (data-accessor fact) engine
                       matrix-type master buf n ofst))
  ([fact n nav ^DenseStorage stor reg matrix-type default engine]
   (let-release [buf (.createDataSource (data-accessor fact) (.capacity stor))]
     (real-packed-matrix fact true buf n 0 nav stor reg matrix-type default engine)))
  ([fact n column? lower? diag-unit?]
   (real-packed-matrix fact n (layout-navigator column?) (packed-storage column? lower? n)
                       (band-region n lower? diag-unit?) :tp (real-default :tp diag-unit?) (tp-engine fact)))
  ([fact n column? lower?]
   (real-packed-matrix fact n (layout-navigator column?) (packed-storage column? lower? n)
                       (band-region n lower?) :sp sy-default (sp-engine fact))))

(defmethod print-method RealPackedMatrix [a ^java.io.Writer w]
  (.write w (str a))
  (print-uplo w a "."))

(defmethod transfer! [RealNativeMatrix RealNativeMatrix]
  [^RealNativeMatrix source ^RealNativeMatrix destination]
  (transfer-matrix-matrix source destination))

(defmethod transfer! [RealPackedMatrix RealPackedMatrix]
  [^RealPackedMatrix source ^RealPackedMatrix destination]
  (transfer-matrix-matrix (= (navigator source) (navigator destination)) source destination))

(defmethod transfer! [clojure.lang.Sequential RealNativeMatrix]
  [source ^RealNativeMatrix destination]
  (transfer-seq-matrix source destination))

(defmethod transfer! [RealNativeVector RealNativeMatrix]
  [^RealNativeVector source ^RealNativeMatrix destination]
  (transfer-vector-matrix source destination))

(defmethod transfer! [RealNativeMatrix RealNativeVector]
  [^RealNativeMatrix source ^RealBlockVector destination]
  (transfer-matrix-vector source destination))

(defmethod transfer! [IntegerNativeVector RealNativeMatrix]
  [^IntegerNativeVector source ^RealNativeMatrix destination]
  (transfer-vector-matrix source destination))

(defmethod transfer! [RealNativeMatrix IntegerNativeVector]
  [^RealNativeMatrix source ^IntegerBlockVector destination]
  (transfer-matrix-vector source destination))

(defmethod transfer! [(Class/forName "[D") RealNativeMatrix]
  [^doubles source ^RealNativeMatrix destination]
  (transfer-array-matrix source destination))

(defmethod transfer! [(Class/forName "[F") RealNativeMatrix]
  [^floats source ^RealNativeMatrix destination]
  (transfer-array-matrix source destination))

(defmethod transfer! [(Class/forName "[J") RealNativeMatrix]
  [^longs source ^RealNativeMatrix destination]
  (transfer-array-matrix source destination))

(defmethod transfer! [(Class/forName "[I") RealNativeMatrix]
  [^ints source ^RealNativeMatrix destination]
  (transfer-array-matrix source destination))

(defmethod transfer! [RealNativeMatrix (Class/forName "[D")]
  [^RealNativeMatrix source ^doubles destination]
  (transfer-matrix-array source destination))

(defmethod transfer! [RealNativeMatrix (Class/forName "[F")]
  [^RealNativeMatrix source ^floats destination]
  (transfer-matrix-array source destination))
