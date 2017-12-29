(ns crypticker.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async  :as async :refer (<! >! put! chan)]
            [reagent.core     :as r     :refer [atom]]
            [secretary.core   :as secretary :include-macros true]
            [accountant.core  :as accountant]
            [cljs-http.client :as http]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [socket.io]))

;; -------------------------
;; Components

(defonce blocks (atom (ring-buffer 5)))
(defonce block-color (atom "#abc"))

(defonce txs (atom (ring-buffer 10)))
(defonce tx-focus (atom {}))
(defonce tx-color (atom "#abc"))

(defonce socket (atom (js/io "https://blockexplorer.com/")))

(defn subscribe
  "subscribes to the transaction channel"
  []
  (.emit @socket "subscribe" "inv"))

(defn add-to
  "returns a function that will add data to a ring buffer with a size"
  [ring]
  (fn [& data]
    (swap! ring into data)))

(defonce add-block (add-to blocks))
(defonce add-tx (add-to txs))

(defn get-blocks
  "gets n most recent blocks"
  [n]
  (go (let [res (<! (http/get "https://blockexplorer.com/api/blocks"
                              {:with-credentials? false
                               :query-params {"limit" n}}))]
        (if (= (:status res) 200)
          (apply add-block (-> res :body :blocks))))))

(defn block-list []
  [:table {:style {:width "400px"}}
   [:thead
    [:tr
     [:th {:style {:text-align "left"}} "Hash"]
     [:th {:style {:text-align "left"}} "Transactions"]
     [:th {:style {:text-align "right"}} "Size"]]]
   (into [:tbody]
         (for [block (reverse @blocks) ; ring buffer elements are backwards
               :let [height (:height block)
                     len (:txlength block)
                     size (:size block)]]
           ^{:key height}
           [:tr.block
            [:td {:style {:text-align "left"}}
             [:pre
              {:style {:color @block-color}}
              (subs height 0 8)]]
            [:td {:style {:text-align "left"}} len]
            [:td {:style {:text-align "right"}} size]]))])

(defn get-tx
  "gets info about a transaction"
  [txid]
  (go (let [res (<! (http/get (str "https://blockexplorer.com/api/tx/" txid)
                              {:with-credentials? false}))]
        (reset! tx-focus (:body res)))))

(defn tx-list []
  [:table {:style {:width "400px"}}
   [:thead
    [:tr
     [:th {:style {:text-align "left"}} "Hash"]
     [:th {:style {:text-align "right"}} "Value"]]]
   (into [:tbody]
         (for [tx (reverse @txs) ; ring buffer elements are backwards
               :let [txid (.-txid tx)
                     value (.-valueOut tx)]]
           ^{:key txid}
           [:tr.tx {:on-click #(get-tx txid)}
            [:td {:style {:text-align "left"}}
             [:pre
              {:style {:color @tx-color}}
              (subs txid 0 8)]]
            [:td {:style {:text-align "right"}}
             [:pre value]]]))])

(defn focused-tx []
  (if (> (count @tx-focus) 0)
    (let [{:keys [valueIn valueOut txid size fees blockheight]} @tx-focus]
      [:div
       [:h4 "Value In"]
       [:div valueIn]
       [:h4 "Value Out"]
       [:div valueOut]
       [:h4 "Hash"]
       [:pre txid]
       [:h4 "Size"]
       [:pre (str size " bytes")]
       [:h4 "Fees"]
       [:div fees]
       [:h4 "Block Height"]
       [:div blockheight]])
    nil))


;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Crypticker"]
   [:div
    [:h3 "Bitcoin"]
    [tx-list]
    [block-list]
    [focused-tx]]
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

  (get-blocks 5)

  ; set up socket.io connection to blockexplorer
  (doto @socket
    (.on "connect" subscribe)
    (.on "block" add-block)
    (.on "tx" add-tx))

  (mount-root))
