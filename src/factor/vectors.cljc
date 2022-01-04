(ns factor.vectors)

;; vec-* utilities originally from https://gist.github.com/sebastibe/27be496c34ba6a3cce3b6425810a3dda
(ty/defn vec-remove
  "Remove elem in coll by index"
  [coll pos] [[:vector :any] :int => [:sequential :any]]
  (concat (subvec coll 0 pos) (subvec coll (inc pos))))

(ty/defn vec-add
  "Add elem in coll by index"
  [coll pos el] [[:vector :any] :int :any => [:sequential :any]]
  (concat (subvec coll 0 pos) [el] (subvec coll pos)))

(ty/defn vec-move
  "Move elem in coll by index"
  [coll pos1 pos2] [[:vector :any] :int :int => [:vector :any]]
  (let [el (nth coll pos1)]
    (if (= pos1 pos2)
      coll
      (into [] (vec-add (vec (vec-remove coll pos1)) pos2 el)))))

(ty/defn vec-swap
  "Swap position of elements in a vector by their indices"
  [v pos1 pos2] [[:vector :any] :int :int => [:vector :any]]
  (assoc v pos2 (v pos1) pos1 (v pos2)))
