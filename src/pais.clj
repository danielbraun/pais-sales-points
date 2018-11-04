(ns pais
  (:require [clj-http.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojure.set :as set]
            [semantic-csv.core :as csv]))

(defn- api [{:as params}]
  (-> (http/get "http://www.pais.co.il/Franchisees/Pages/RequestsHandler.ashx"
                {:as :stream
                 :query-params (merge {:Command "SalesPoints" :MapType "0"}
                                      params)})
      :body
      html/html-resource))

(defn- cities []
  (->> (html/select (api {}) [:ul.Cities :li])
       (map (comp (fn [{:keys [cityname]}] {:CityName cityname})
                  :attrs first :content))))

(defn- tr->point [tr]
  (merge (-> tr (html/select [:a]) first :attrs)
         (->> (html/select tr [:td])
              (take 3)
              (map (comp first :content))
              (zipmap [:street :house-number :type]))))

(defn- parse-sales-points [dom]
  {:items (->> (html/select dom [:#PaisRegionsSalesPoints :tr])
               rest
               (map tr->point))
   :pages (or (some-> dom
                      (html/select [:li.PagerLast :a])
                      first
                      :attrs
                      :page
                      Integer/parseInt) 1)})

(defn sales-points [params]
  (let [{:keys [pages items]} (-> params api parse-sales-points)]
    (->> (range 2 (inc pages))
         (map #(merge params {:Page %}))
         (pmap (comp :items parse-sales-points api))
         (apply concat items)
         (map #(merge % params)))))

(defn all-sales-points []
  (->> (cities)
       (pmap sales-points)
       (apply concat)))

(comment (csv/spit-csv "sales-points.csv" (all-sales-points)))
