(ns am-i-an-otter.core
  (:use compojure.core)
  (:use hiccup.core)
  (:require (clojure.contrib [sql :as sql])
            (clojure.contrib [duck-streams :as ds])))


(import '(java.nio.file AccessDeniedException FileSystems FileVisitResult Files Path Paths PathMatcher SimpleFileVisitor))

(def otter-img-dir "resources/public/img/")

(def otter-img-dir-fq "/home/bje/projects/am-i-an-otter/resources/public/img")

; Instead of starting with an empty map should scan the disk and see what's there at startup
; The next few functions work towards that goal

(defn make-matcher [pattern] (.getPathMatcher (FileSystems/getDefault) (str "glob:" pattern)))

; Returns the trimmed filename, if it matches using the matcher 
(defn file-find [file matcher] (let [fname (.getName file (- (.getNameCount file) 1)) my-matcher matcher] 
	(.info (get-logger) (str "Accessing file " fname))
  (if (and (not (nil? fname)) (.matches matcher fname))
    ; This is (toString) to allow the :img tags to work properly
    (.toString fname)
    nil)))

; Gets the next id for a map keyed on id numbers (eg the otters)
(defn next-map-id [map-with-id] (+ 1 (nth (max (let [map-ids (keys map-with-id)] 
                                (if (nil? map-ids)
                                  [0]
                                  map-ids))) 0 )))

; Ref-alter function for adding a filename to the file map
(defn alter-file-map [file-map fname] (assoc file-map (next-map-id file-map) fname))

; Define the file scanner proxy
(defn make-scanner [pattern file-map-r] (let [matcher (make-matcher pattern)]
  (proxy [SimpleFileVisitor] []
    (visitFile [file attribs] (let [my-file file, my-attrs attribs, file-name (file-find my-file matcher)]
      (.info (get-logger) (str "Return from file-find " file-name))      
      (if (not (nil? file-name)) 
        (dosync (alter file-map-r alter-file-map file-name) file-map-r)
        nil)        
      (.info (get-logger) (str "After return from file-find " @file-map-r))
      FileVisitResult/CONTINUE))
    
    (visitFileFailed [file exc] (let [my-file file my-ex exc]
      (.info (get-logger) (str "Failed to access file " my-file " ; Exception: " my-ex))
      FileVisitResult/CONTINUE)))))

; Files.walkFileTree(startingDir, finder);
(defn scan-for-otters [file-map] (let [my-map file-map]
  (Files/walkFileTree (Paths/get otter-img-dir-fq (into-array String [])) (make-scanner "*.jpg" my-map))
  my-map
  ))

; otter-pics maps integer ids to filenames
(def otter-pics (deref (scan-for-otters (ref {}))))

(defn otter-exists [id] (contains? (keys otter-pics) id))

; Fn for upvoting an otter in the DB
(defn upvote-sqlite [id]
  (if (otter-exists id) 
    (let [my-id id]
      (.info (get-logger) (str "Upvoted Otter " id))                           
      nil) ; stubbed out for now
    nil))

; Function returns a random id for an otter
(defn random-otter [] (rand-nth (keys otter-pics)))

(defn upload-otter [req]
  (let [new-id (next-map-id otter-pics), new-name (str (java.util.UUID/randomUUID) ".jpg"), tmp-file (:tempfile (get (:multipart-params req) "file"))]
       (.info (get-logger) (str (.toString req) " ; New name = " new-name " ; New id = " new-id))
       (ds/copy tmp-file (ds/file-str (str otter-img-dir new-name)))
       (def otter-pics (assoc otter-pics new-id new-name))
       (str "<h1>Otter Uploaded!</h1>")))
