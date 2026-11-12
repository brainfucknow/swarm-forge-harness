(ns sf.record
  "Tasks as plain values: ids, record commit messages and their trailers, the
  title and request of a task, and the key: value blocks that sf start and sf
  show print."
  (:refer-clojure :exclude [resolve])
  (:require [clojure.string :as str]))

(def ^:private slug-limit 30)

(def ^:private bases
  "Each letter of U+00C0..U+024F and U+1E00..U+1EFF that has a canonical
  decomposition, followed by the letter its decomposition starts with: the
  rest of the decomposition is combining marks, which slug drops."
  (apply hash-map (seq (str
   "ÀAÁAÂAÃAÄAÅAÇCÈEÉEÊEËEÌIÍIÎIÏIÑNÒOÓOÔOÕOÖOÙUÚUÛU"
   "ÜUÝYàaáaâaãaäaåaçcèeéeêeëeìiíiîiïiñnòoóoôoõoöoùu"
   "úuûuüuýyÿyĀAāaĂAăaĄAąaĆCćcĈCĉcĊCċcČCčcĎDďdĒEēeĔE"
   "ĕeĖEėeĘEęeĚEěeĜGĝgĞGğgĠGġgĢGģgĤHĥhĨIĩiĪIīiĬIĭiĮI"
   "įiİIĴJĵjĶKķkĹLĺlĻLļlĽLľlŃNńnŅNņnŇNňnŌOōoŎOŏoŐOőo"
   "ŔRŕrŖRŗrŘRřrŚSśsŜSŝsŞSşsŠSšsŢTţtŤTťtŨUũuŪUūuŬUŭu"
   "ŮUůuŰUűuŲUųuŴWŵwŶYŷyŸYŹZźzŻZżzŽZžzƠOơoƯUưuǍAǎaǏI"
   "ǐiǑOǒoǓUǔuǕUǖuǗUǘuǙUǚuǛUǜuǞAǟaǠAǡaǢÆǣæǦGǧgǨKǩkǪO"
   "ǫoǬOǭoǮƷǯʒǰjǴGǵgǸNǹnǺAǻaǼÆǽæǾØǿøȀAȁaȂAȃaȄEȅeȆEȇe"
   "ȈIȉiȊIȋiȌOȍoȎOȏoȐRȑrȒRȓrȔUȕuȖUȗuȘSșsȚTțtȞHȟhȦAȧa"
   "ȨEȩeȪOȫoȬOȭoȮOȯoȰOȱoȲYȳyḀAḁaḂBḃbḄBḅbḆBḇbḈCḉcḊDḋd"
   "ḌDḍdḎDḏdḐDḑdḒDḓdḔEḕeḖEḗeḘEḙeḚEḛeḜEḝeḞFḟfḠGḡgḢHḣh"
   "ḤHḥhḦHḧhḨHḩhḪHḫhḬIḭiḮIḯiḰKḱkḲKḳkḴKḵkḶLḷlḸLḹlḺLḻl"
   "ḼLḽlḾMḿmṀMṁmṂMṃmṄNṅnṆNṇnṈNṉnṊNṋnṌOṍoṎOṏoṐOṑoṒOṓo"
   "ṔPṕpṖPṗpṘRṙrṚRṛrṜRṝrṞRṟrṠSṡsṢSṣsṤSṥsṦSṧsṨSṩsṪTṫt"
   "ṬTṭtṮTṯtṰTṱtṲUṳuṴUṵuṶUṷuṸUṹuṺUṻuṼVṽvṾVṿvẀWẁwẂWẃw"
   "ẄWẅwẆWẇwẈWẉwẊXẋxẌXẍxẎYẏyẐZẑzẒZẓzẔZẕzẖhẗtẘwẙyẛſẠA"
   "ạaẢAảaẤAấaẦAầaẨAẩaẪAẫaẬAậaẮAắaẰAằaẲAẳaẴAẵaẶAặaẸE"
   "ẹeẺEẻeẼEẽeẾEếeỀEềeỂEểeỄEễeỆEệeỈIỉiỊIịiỌOọoỎOỏoỐO"
   "ốoỒOồoỔOổoỖOỗoỘOộoỚOớoỜOờoỞOởoỠOỡoỢOợoỤUụuỦUủuỨU"
   "ứuỪUừuỬUửuỮUữuỰUựuỲYỳyỴYỵyỶYỷyỸYỹy"))))

(def ^:private folds
  (merge bases {\å "aa" \ä "ae" \æ "ae" \ö "oe" \ø "oe"}))

(defn slug
  "The title in lowercase ASCII, cut to at most 30 characters just before a -:
  å becomes aa, ä and æ ae, ö and ø oe, other accented letters lose their
  accents, and each run of other characters becomes one -."
  [title]
  (let [plain (-> (str/lower-case title)
                  (str/escape folds)
                  (str/replace #"\p{M}" "")
                  (str/replace #"[^a-z0-9]+" "-")
                  (str/replace #"^-|-$" ""))
        cut (str/last-index-of plain "-" slug-limit)]
    (cond (str/blank? plain) "task"
          (<= (count plain) slug-limit) plain
          cut (subs plain 0 cut)
          :else (subs plain 0 slug-limit))))

(defn hex-part [id]
  (last (str/split id #"-")))

(defn branch [id]
  (str "swarm/" id))

(defn new-id
  "slug-hex for the title, with the first of the hex candidates that taken
  does not hold."
  [title taken candidates]
  (str (slug title) "-" (first (remove (set taken) candidates))))

(defn resolve
  "[:ok id] for the full id, the hex part of one id, or a prefix of one id;
  [:ambiguous ids] or [:none] otherwise."
  [arg ids]
  (let [ids (sort (distinct ids))
        hex (filter #(= arg (hex-part %)) ids)
        prefixed (filter #(str/starts-with? % arg) ids)]
    (cond (some #{arg} ids) [:ok arg]
          (= 1 (count hex)) [:ok (first hex)]
          (= 1 (count prefixed)) [:ok (first prefixed)]
          (seq (concat hex prefixed)) [:ambiguous (sort (distinct (concat hex prefixed)))]
          :else [:none])))

(defn free-text
  "Arguments joined with spaces, trimmed at both ends."
  [args]
  (str/trim (str/join " " args)))

(def ^:private trailers
  [["SwarmForge-Task" :task] ["SwarmForge-Record" :record] ["SwarmForge-From" :from]
   ["SwarmForge-Stage" :stage] ["SwarmForge-Route" :route] ["SwarmForge-Needs" :needs]
   ["SwarmForge-Merged-Into" :merged-into] ["SwarmForge-Reviewed" :reviewed]
   ["SwarmForge-Audit-Refusals" :audit-refusals] ["SwarmForge-Verdict" :verdict]])

(defn rejected-refusal
  "Why value cannot be the value of a `Rejected: <finding> | <reason>` trailer,
  or nil: it must be one line, split at its last ` | ` into a finding and a
  reason that are not blank."
  [value]
  (let [at (str/last-index-of value " | ")]
    (cond (re-find #"[\r\n]" value) "--rejected takes one line"
          (nil? at) (str "--rejected needs '<finding> | <reason>', got '" value "'")
          (or (str/blank? (subs value 0 at)) (str/blank? (subs value (+ at 3))))
          (str "--rejected needs a finding and a reason around its last ' | ', got '" value "'"))))

(defn rejected-finding
  "The :finding and :reason of a `Rejected:` value, split at its last ` | `, and
  the :kind and :severity of a finding that starts with `<KIND>, <SEVERITY>: `
  (KIND DEFECT or PREMISE, SEVERITY CRITICAL, HIGH, MEDIUM or LOW), else nil."
  [value]
  (let [at (str/last-index-of value " | ")
        finding (if at (subs value 0 at) value)
        [_ kind severity] (re-find #"^(DEFECT|PREMISE), (CRITICAL|HIGH|MEDIUM|LOW): " finding)]
    {:finding finding :reason (when at (subs value (+ at 3))) :kind kind :severity severity}))

(defn- lore-trailers
  "The Lore-id and Rejected trailer lines of a record that has a :lore-id."
  [{:keys [lore-id rejected]}]
  (when lore-id
    (cons (str "Lore-id: " lore-id) (map #(str "Rejected: " %) rejected))))

(defn message
  "The commit message of a record."
  [{:keys [task record text] :as rec}]
  (str "sf " record ": " task
       (when-not (str/blank? text) (str "\n\n" text))
       "\n\n"
       (apply str (for [line (concat (lore-trailers rec)
                                     (for [[key field] trailers :when (rec field)]
                                       (str key ": " (rec field))))]
                    (str line "\n")))))

(defn- message-text
  "The paragraphs between the subject and the trailer block."
  [message]
  (let [body (str/trim (str/replace message #"\A[^\n]*" ""))
        gap (str/last-index-of body "\n\n")]
    (when gap (str/trim (subs body 0 gap)))))

(defn parse
  "The record in a commit, given as :sha, :parents, :message and :trailers
  (the unfolded trailer lines). Its :commit is its own sha for a created
  record, else its first parent: the role's commit it was made on. An
  approval also has the values of its `Rejected:` trailers, in order, as
  :rejected."
  [{:keys [sha parents message] :as commit}]
  (let [pairs (for [line (:trailers commit)
                    :let [[_ k v] (re-matches #"([^:]+): ?(.*)" line)]
                    :when k]
                [k v])
        by-key (into {} pairs)
        fields (into {} (for [[key field] trailers :when (by-key key)] [field (by-key key)]))
        text (message-text message)]
    (cond-> (assoc fields :sha sha
                   :commit (if (= "created" (:record fields)) sha (first parents)))
      (not (str/blank? text)) (assoc :text text)
      (= "approval" (:record fields)) (assoc :rejected (vec (for [[k v] pairs :when (= "Rejected" k)] v))))))

(defn same-commit?
  "True when a and b name the same commit, each a full or an abbreviated sha."
  [a b]
  (boolean (and (not (str/blank? a)) (not (str/blank? b))
                (or (str/starts-with? a b) (str/starts-with? b a)))))

(defn role-commits
  "The records, oldest first, with each :commit that names an earlier record
  replaced by that record's :commit: the role's commit under them."
  [records]
  (reduce (fn [done rec]
            (let [by-sha (into {} (map (juxt :sha :commit) done))]
              (conj done (update rec :commit #(get by-sha % %)))))
          []
          records))

(defn files-value
  "Paths as one value: - when there are none, at most 40 listed."
  [paths]
  (let [limit 40]
    (cond (empty? paths) "-"
          (> (count paths) limit) (str (str/join "," (take limit paths)) ",+" (- (count paths) limit) " more")
          :else (str/join "," paths))))

(defn milestone?
  "True for the records that name a role's commit: created, review and handoff."
  [{:keys [record]}]
  (contains? #{"created" "review" "handoff"} record))

(defn with-files
  "The records with :files on each review and handoff: (diff from to) paths
  since the commit of the previous created, review or handoff record."
  [records diff]
  (first (reduce (fn [[done previous] {:keys [record commit] :as rec}]
                   (if (milestone? rec)
                     [(conj done (cond-> rec (not= "created" record) (assoc :files (files-value (diff previous commit)))))
                      commit]
                     [(conj done rec) previous]))
                 [[] nil]
                 records)))

(def ^:private brief-kept
  "The records that a brief always keeps."
  #{"question" "answer" "changes-requested"})

(defn brief
  "The records with all but the last limit of the records that are not
  question, answer or changes-requested left out, in order, as :records, and
  the number left out as :omitted."
  [records limit]
  (let [other? #(not (brief-kept (:record %)))
        omitted (max 0 (- (count (filter other? records)) limit))
        dropped (set (take omitted (keep-indexed #(when (other? %2) %1) records)))]
    {:records (keep-indexed #(when-not (dropped %1) %2) records)
     :omitted omitted}))

(defn- fields [{:keys [task record from verdict text stage route commit files merged-into audit-refusals]}]
  (concat
   [["type" record] ["from" from]]
   (when verdict [["verdict" verdict]])
   (case record
     "created" [["branch" (branch task)] ["commit" commit] ["route" route]]
     "question" [["question" text]]
     "answer" [["answer" text]]
     "review" [["commit" commit] ["files" files] ["summary" text]]
     ("changes-requested" "verdict") [["reason" text]]
     "route" [["route" route]]
     "handoff" [["to" stage] ["branch" (branch task)] ["commit" commit] ["files" files] ["summary" text]]
     "finished" [["merged-into" merged-into]]
     "moved" [["path" text]]
     [])
   (when (and audit-refusals (#{"review" "handoff"} record))
     [["audit-refusals" audit-refusals]])))

(defn block
  "The record as key: value lines; later lines of a value are indented by two spaces."
  [rec]
  (str/join "\n" (for [[key value] (fields rec)]
                   (str key ": " (str/replace (str value) "\n" "\n  ")))))

(defn one-line
  "text with each run of whitespace, line breaks included, as one space, trimmed."
  [text]
  (str/join " " (str/split (str/trim text) #"\s+")))

(defn created-text
  "The free text of a created record: the title, a blank line, the request."
  [title request]
  (str title "\n\n" (str/trim request)))

(defn title-and-request
  "The :title and :request in the free text of a created record."
  [text]
  (let [[title request] (str/split (or text "") #"\n\n" 2)]
    {:title title :request (str/trim (or request ""))}))

(defn read-task-file
  "The :title and :request of a task file, as tasks created before sf kept
  them in the created record have it."
  [text]
  (let [[_ title request] (re-matches #"(?s)---\n.*?\n---\n\s*# ([^\n]*)\n?(.*)" text)]
    {:title title :request (str/trim (or request ""))}))

(defn record-trailer
  "The first key that starts with SwarmForge-, in any letter case, or nil."
  [keys]
  (first (filter #(str/starts-with? (str/lower-case %) "swarmforge-") keys)))
