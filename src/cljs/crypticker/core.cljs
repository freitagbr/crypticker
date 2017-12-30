(ns crypticker.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async  :as async :refer (<! >! put! chan)]
            [reagent.core     :as r     :refer [atom]]
            [secretary.core   :as secretary :include-macros true]
            [accountant.core  :as accountant]
            [cljs-http.client :as http]
            [goog.i18n.NumberFormat.Format])
  (:import (goog.i18n NumberFormat)
           (goog.i18n.NumberFormat Format)))

;; -------------------------
;; Utilities

(defonce nff (NumberFormat. Format/CURRENCY))

(defn- nf [n]
  (.format nff (str n)))

(defn- amt [cx]
  (nf (js/parseFloat cx)))

;; -------------------------
;; Components

(defonce cxs (atom (sorted-map)))

(defn- assoc-new-cx
  "associates new currency info to currency map"
  [cxs cx]
  (let [cxid (:currency_id cx)
        new-price (:vwap cx)
        old-price (get-in cxs [cxid :vwap])
        cmp (if (some? old-price)
              (compare new-price old-price)
              0)]
    (assoc cxs cxid (assoc cx :cmp cmp))))

(defn get-market-info
  "gets current market information"
  []
  (go
    (let [res (<! (http/get "https://marketapi-vdev.blockexplorer.com/tickers"
                            {:with-credentials? false}))
          tickers (:tickers (:body res))]
      (if (= 200 (:status res))
        (doall
          (for [cx tickers]
            (swap! cxs assoc-new-cx cx)))))))

(defonce currency-update
  (js/setInterval get-market-info 5000))

(defn cx-name [cx]
  [:div.cx-name (:currency_name cx)])

(defn cx-code [cx]
  [:div.cx-code (:currency_code cx)])

(defn cx-handle-and-price [cx]
  (let [cmp (:cmp cx)
        price (amt (:vwap cx))]
    [:div.row
     [:div.group
      [cx-name cx]
      [(cond
         (> cmp 0) :span.circle.green
         (< cmp 0) :span.circle.red
         :else :span.circle)]
      [cx-code cx]]
     (cond
       (> cmp 0) [:div.group
                  [:div.price.pos price]
                  [:div.chev-up]]
       (< cmp 0) [:div.group
                  [:div.price.neg price]
                  [:div.chev-down]]
       :else [:div.group
              [:div.price price]
              [:div.chev-flat]])]))

(defn cx-market-cap [cx]
  [:div.cap (amt (:market_cap cx))])

(defn cx-volume-24h [cx]
  [:div.vol (str (amt (:volume_24h cx)) " in 24h")])

(defn cx-supply [cx]
  [:div.supply (amt (:circulating_supply cx))])

(defn market-info []
  (into [:div.cxs]
        (for [[cid cx] @cxs]
          ^{:key cid}
          [:div.cx
           [cx-handle-and-price cx]
           [:div.row
            [cx-market-cap cx]
            [cx-volume-24h cx]]
           [:div.row
            [cx-supply cx]]])))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Crypticker"]
   [:div
    [market-info]]
   [:div [:a {:href "/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About crypticker"]
   [:div [:a {:href "/"} "go to the home page"]]])

;; -------------------------
;; Routes

(def page (atom #'home-page))

(defn current-page []
  [:div [@page]])

(secretary/defroute "/" []
  (reset! page #'home-page))

(secretary/defroute "/about" []
  (reset! page #'about-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (get-market-info)
  (mount-root))
