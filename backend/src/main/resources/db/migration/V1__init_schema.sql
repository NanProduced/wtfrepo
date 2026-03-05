-- V1 schema freeze (entity-driven)
-- Source of truth: backend JPA entities as of 2026-03-02

-- =========================
-- auth
-- =========================
CREATE TABLE auth_user (
  user_id VARCHAR(64) PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  username_changed BOOLEAN NOT NULL,
  bug_balance BIGINT NOT NULL CHECK (bug_balance >= 0),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_auth_user_username UNIQUE (username)
);

CREATE TABLE auth_identity (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_subject VARCHAR(128) NOT NULL,
  linked_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_auth_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_auth_identity_user ON auth_identity (user_id);
CREATE INDEX idx_auth_identity_user_provider ON auth_identity (user_id, provider);

CREATE TABLE auth_oauth_state (
  state VARCHAR(255) PRIMARY KEY,
  consumed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_oauth_state_consumed_at ON auth_oauth_state (consumed_at);

CREATE TABLE auth_exchange_idempotency (
  idempotency_key VARCHAR(128) PRIMARY KEY,
  request_fingerprint VARCHAR(64) NOT NULL,
  access_token TEXT NOT NULL,
  token_expires_in BIGINT NOT NULL,
  token_expires_at TIMESTAMPTZ NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  username VARCHAR(64) NOT NULL,
  username_changed BOOLEAN NOT NULL,
  bug_balance BIGINT NOT NULL CHECK (bug_balance >= 0),
  is_new_user BOOLEAN NOT NULL,
  initial_bug_grant INTEGER NOT NULL CHECK (initial_bug_grant >= 0),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_exchange_idempotency_created_at
  ON auth_exchange_idempotency (created_at);

-- =========================
-- admin
-- =========================
CREATE TABLE admin_user_role (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  granted_by VARCHAR(64),
  granted_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  is_active BOOLEAN NOT NULL,
  CONSTRAINT uk_admin_user_role UNIQUE (user_id, role)
);

CREATE INDEX idx_admin_user_role_user_active
  ON admin_user_role (user_id, is_active);
CREATE INDEX idx_admin_user_role_role_active
  ON admin_user_role (role, is_active);

CREATE TABLE admin_bootstrap_record (
  id VARCHAR(64) PRIMARY KEY,
  admin_user_id VARCHAR(64) NOT NULL,
  email_used VARCHAR(255) NOT NULL,
  bootstrapped_at TIMESTAMPTZ NOT NULL,
  ip_address VARCHAR(64),
  user_agent TEXT
);

CREATE INDEX idx_admin_bootstrap_bootstrapped_at
  ON admin_bootstrap_record (bootstrapped_at DESC);

CREATE TABLE admin_audit_log (
  id VARCHAR(64) PRIMARY KEY,
  operator_id VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(255) NOT NULL,
  before_snapshot TEXT,
  after_snapshot TEXT,
  metadata TEXT,
  request_id VARCHAR(100),
  ip_address VARCHAR(64),
  user_agent TEXT,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_audit_created_at
  ON admin_audit_log (created_at DESC);
CREATE INDEX idx_admin_audit_operator_created
  ON admin_audit_log (operator_id, created_at DESC);
CREATE INDEX idx_admin_audit_action
  ON admin_audit_log (action);
CREATE INDEX idx_admin_audit_target
  ON admin_audit_log (target_type, target_id);

CREATE TABLE safety_ticket (
  id VARCHAR(64) PRIMARY KEY,
  source VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reporter_id VARCHAR(64),
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(255) NOT NULL,
  reason TEXT NOT NULL,
  resolution TEXT,
  resolved_by VARCHAR(64),
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_safety_ticket_status_created
  ON safety_ticket (status, created_at DESC);
CREATE INDEX idx_safety_ticket_target_status_created
  ON safety_ticket (target_type, target_id, status, created_at DESC);
CREATE INDEX idx_safety_ticket_created_at
  ON safety_ticket (created_at DESC);

CREATE TABLE user_ban_record (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  ban_type VARCHAR(32) NOT NULL,
  reason TEXT NOT NULL,
  banned_by VARCHAR(64) NOT NULL,
  banned_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ,
  unbanned_by VARCHAR(64),
  unbanned_at TIMESTAMPTZ,
  is_active BOOLEAN NOT NULL
);

CREATE INDEX idx_user_ban_record_user_active
  ON user_ban_record (user_id, is_active);
CREATE INDEX idx_user_ban_record_active_banned_at
  ON user_ban_record (is_active, banned_at DESC);
CREATE INDEX idx_user_ban_record_banned_at
  ON user_ban_record (banned_at DESC);

CREATE TABLE notification_alert_task (
  id VARCHAR(64) PRIMARY KEY,
  alert_type VARCHAR(32) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  target_type VARCHAR(32),
  target_id VARCHAR(255),
  message TEXT NOT NULL,
  email_sent_to TEXT,
  email_sent_at TIMESTAMPTZ,
  is_acknowledged BOOLEAN NOT NULL,
  acknowledged_by VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_alert_ack_created
  ON notification_alert_task (is_acknowledged, created_at DESC);
CREATE INDEX idx_notification_alert_created
  ON notification_alert_task (created_at DESC);

-- =========================
-- specimen
-- =========================
CREATE TABLE specimen (
  specimen_id VARCHAR(64) PRIMARY KEY,
  repo_full_name VARCHAR(256) NOT NULL,
  github_url VARCHAR(512) NOT NULL,
  note TEXT,
  status VARCHAR(32) NOT NULL,
  reviewed_by VARCHAR(64),
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_specimen_status ON specimen (status);
CREATE INDEX idx_specimen_status_updated
  ON specimen (status, updated_at DESC);
CREATE INDEX idx_specimen_repo_full_name_lower
  ON specimen (LOWER(repo_full_name));

CREATE TABLE specimen_github_metadata (
  specimen_id VARCHAR(64) PRIMARY KEY,
  repo_id BIGINT,
  repo_html_url VARCHAR(512),
  owner_login VARCHAR(128),
  owner_id VARCHAR(64),
  owner_avatar_url VARCHAR(512),
  owner_html_url VARCHAR(512),
  description TEXT,
  homepage VARCHAR(512),
  default_branch VARCHAR(128),
  languages_json TEXT,
  topics_json TEXT,
  license_spdx_id VARCHAR(64),
  license_name VARCHAR(128),
  visibility VARCHAR(32),
  archived BOOLEAN NOT NULL,
  fork BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ,
  updated_at_remote TIMESTAMPTZ,
  stargazers_count BIGINT NOT NULL,
  forks_count BIGINT NOT NULL,
  open_issues_count BIGINT NOT NULL,
  pushed_at TIMESTAMPTZ,
  metadata_synced_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE specimen_arena_metrics (
  specimen_id VARCHAR(64) PRIMARY KEY,
  elo INTEGER NOT NULL,
  hype DOUBLE PRECISION NOT NULL,
  votes BIGINT NOT NULL,
  delta_24h INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_specimen_arena_metrics_elo
  ON specimen_arena_metrics (elo DESC, specimen_id);
CREATE INDEX idx_specimen_arena_metrics_hype
  ON specimen_arena_metrics (hype DESC, specimen_id);

CREATE TABLE specimen_community_metrics (
  specimen_id VARCHAR(64) PRIMARY KEY,
  comment_count BIGINT NOT NULL,
  top_roast_comment_id VARCHAR(64),
  top_roast_resonance_count INTEGER NOT NULL,
  top_roast_chief_conclusion BOOLEAN NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_specimen_community_metrics_top_roast
  ON specimen_community_metrics (top_roast_resonance_count DESC);

CREATE TABLE specimen_official_commentary (
  specimen_id VARCHAR(64) PRIMARY KEY,
  one_liner_zh TEXT,
  one_liner_en TEXT,
  arena_reason_zh TEXT,
  arena_reason_en TEXT,
  updated_by VARCHAR(64) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE specimen_readme_excerpt (
  excerpt_id VARCHAR(64) PRIMARY KEY,
  specimen_id VARCHAR(64) NOT NULL,
  excerpt_type VARCHAR(32) NOT NULL,
  text TEXT NOT NULL,
  translated_text_zh TEXT,
  translation_meta_json TEXT,
  candidate_id VARCHAR(64),
  priority INTEGER NOT NULL,
  curated_by VARCHAR(64) NOT NULL,
  curated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_specimen_readme_excerpt_specimen_priority
  ON specimen_readme_excerpt (specimen_id, priority, excerpt_id);

CREATE TABLE specimen_code_highlight (
  highlight_id VARCHAR(64) PRIMARY KEY,
  specimen_id VARCHAR(64) NOT NULL,
  title VARCHAR(256) NOT NULL,
  code_language VARCHAR(64) NOT NULL,
  snippet TEXT NOT NULL,
  explain_text TEXT NOT NULL,
  candidate_id VARCHAR(64),
  priority INTEGER NOT NULL,
  status VARCHAR(32) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_specimen_code_highlight_specimen_priority
  ON specimen_code_highlight (specimen_id, priority, highlight_id);

CREATE TABLE specimen_repo_identity (
  id VARCHAR(64) PRIMARY KEY,
  specimen_id VARCHAR(64) NOT NULL,
  github_user_id VARCHAR(64) NOT NULL,
  github_login VARCHAR(128) NOT NULL,
  github_avatar_url VARCHAR(512),
  github_html_url VARCHAR(512),
  role VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  active BOOLEAN NOT NULL,
  contributions INTEGER,
  synced_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_specimen_repo_identity_user UNIQUE (specimen_id, github_user_id)
);

CREATE INDEX idx_specimen_repo_identity_specimen_active
  ON specimen_repo_identity (specimen_id, active);
CREATE INDEX idx_specimen_repo_identity_specimen_active_user
  ON specimen_repo_identity (specimen_id, active, github_user_id);

CREATE TABLE tag_dimension (
  dimension_key VARCHAR(64) PRIMARY KEY,
  name_zh VARCHAR(128) NOT NULL,
  name_en VARCHAR(128) NOT NULL,
  description TEXT,
  select_mode VARCHAR(32) NOT NULL,
  min_select INTEGER,
  max_select INTEGER,
  required BOOLEAN NOT NULL,
  match_enabled BOOLEAN NOT NULL,
  default_weight NUMERIC(10, 4),
  sort_order INTEGER NOT NULL,
  ui_meta_json TEXT,
  status VARCHAR(32) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_tag_dimension_sort
  ON tag_dimension (sort_order, dimension_key);

CREATE TABLE tag_definition (
  dimension_key VARCHAR(64) NOT NULL,
  tag_key VARCHAR(128) NOT NULL,
  name_zh VARCHAR(128) NOT NULL,
  name_en VARCHAR(128) NOT NULL,
  description TEXT,
  display_order INTEGER NOT NULL,
  match_weight NUMERIC(10, 4),
  ui_meta_json TEXT,
  status VARCHAR(32) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (dimension_key, tag_key)
);

CREATE INDEX idx_tag_definition_dimension_order
  ON tag_definition (dimension_key, display_order, tag_key);
CREATE INDEX idx_tag_definition_tag_key
  ON tag_definition (tag_key);

CREATE TABLE specimen_tag (
  specimen_id VARCHAR(64) NOT NULL,
  dimension_key VARCHAR(64) NOT NULL,
  tag_key VARCHAR(128) NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (specimen_id, dimension_key, tag_key)
);

CREATE INDEX idx_specimen_tag_dimension_tag
  ON specimen_tag (dimension_key, tag_key);

CREATE TABLE user_watchlist_item (
  item_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  specimen_id VARCHAR(64) NOT NULL,
  source VARCHAR(64) NOT NULL,
  added_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_user_watchlist_item_user_specimen UNIQUE (user_id, specimen_id)
);

CREATE INDEX idx_user_watchlist_item_user_added
  ON user_watchlist_item (user_id, added_at DESC, item_id);

CREATE TABLE specimen_admin_action_idempotency (
  operation_key VARCHAR(64) PRIMARY KEY,
  admin_user_id VARCHAR(64) NOT NULL,
  specimen_id VARCHAR(64) NOT NULL,
  operation VARCHAR(16) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  response_status VARCHAR(32) NOT NULL,
  response_reviewed_by VARCHAR(64),
  response_reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_specimen_admin_action_idempotency_scope UNIQUE (
    admin_user_id,
    specimen_id,
    operation,
    idempotency_key
  )
);

CREATE INDEX idx_specimen_admin_action_created_at
  ON specimen_admin_action_idempotency (created_at);

-- =========================
-- arena
-- =========================
CREATE TABLE specimen_rating (
  specimen_id VARCHAR(64) PRIMARY KEY,
  elo_score INTEGER NOT NULL,
  calibrated_score INTEGER,
  matches_played INTEGER NOT NULL,
  ipo_status VARCHAR(32) NOT NULL,
  k_factor INTEGER NOT NULL,
  elo_open_today INTEGER NOT NULL,
  delta_r_7d_stddev DOUBLE PRECISION,
  recent_appearances INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_specimen_rating_elo_score
  ON specimen_rating (elo_score DESC, specimen_id);
CREATE INDEX idx_specimen_rating_ipo_status
  ON specimen_rating (ipo_status);

CREATE TABLE specimen_match_pair (
  left_specimen_id VARCHAR(64) NOT NULL,
  right_specimen_id VARCHAR(64) NOT NULL,
  match_type VARCHAR(32) NOT NULL,
  match_score INTEGER NOT NULL,
  match_profile_version VARCHAR(64) NOT NULL,
  computed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (left_specimen_id, right_specimen_id)
);

CREATE INDEX idx_specimen_match_pair_match_score
  ON specimen_match_pair (match_score DESC, left_specimen_id, right_specimen_id);
CREATE INDEX idx_specimen_match_pair_right_specimen
  ON specimen_match_pair (right_specimen_id);

CREATE TABLE battle_vote (
  vote_id VARCHAR(64) PRIMARY KEY,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint VARCHAR(256) NOT NULL,
  battle_id VARCHAR(160) NOT NULL,
  left_specimen_id VARCHAR(64) NOT NULL,
  right_specimen_id VARCHAR(64) NOT NULL,
  winner VARCHAR(16) NOT NULL,
  voter_id VARCHAR(64) NOT NULL,
  bug_cost INTEGER NOT NULL,
  left_elo_before INTEGER NOT NULL,
  left_elo_delta INTEGER NOT NULL,
  right_elo_before INTEGER NOT NULL,
  right_elo_delta INTEGER NOT NULL,
  left_elo_after INTEGER NOT NULL,
  right_elo_after INTEGER NOT NULL,
  left_k_factor INTEGER NOT NULL,
  right_k_factor INTEGER NOT NULL,
  match_type VARCHAR(32) NOT NULL,
  match_profile_version VARCHAR(64) NOT NULL,
  left_phase VARCHAR(32) NOT NULL,
  right_phase VARCHAR(32) NOT NULL,
  wallet_balance_after BIGINT NOT NULL,
  policy_snapshot TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_battle_vote_idempotency_key UNIQUE (idempotency_key),
  CONSTRAINT uk_battle_vote_battle_voter UNIQUE (battle_id, voter_id)
);

CREATE INDEX idx_battle_vote_created_at
  ON battle_vote (created_at);
CREATE INDEX idx_battle_vote_voter_created
  ON battle_vote (voter_id, created_at);
CREATE INDEX idx_battle_vote_left_specimen_created
  ON battle_vote (left_specimen_id, created_at);
CREATE INDEX idx_battle_vote_right_specimen_created
  ON battle_vote (right_specimen_id, created_at);

CREATE TABLE elo_daily_snapshot (
  specimen_id VARCHAR(64) NOT NULL,
  date DATE NOT NULL,
  elo_open INTEGER NOT NULL,
  elo_close INTEGER NOT NULL,
  delta_r INTEGER NOT NULL,
  matches_count INTEGER NOT NULL,
  both_bad_count INTEGER NOT NULL,
  global_correction INTEGER NOT NULL,
  PRIMARY KEY (specimen_id, date)
);

CREATE INDEX idx_elo_daily_snapshot_date
  ON elo_daily_snapshot (date);
CREATE INDEX idx_elo_daily_snapshot_date_close
  ON elo_daily_snapshot (date, elo_close DESC, specimen_id);

-- =========================
-- economy
-- =========================
CREATE TABLE economy_wallet (
  user_id VARCHAR(64) PRIMARY KEY,
  balance BIGINT NOT NULL CHECK (balance >= 0),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE economy_ledger (
  ledger_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  entry_type VARCHAR(32) NOT NULL,
  delta BIGINT NOT NULL,
  balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
  ref_type VARCHAR(64) NOT NULL,
  ref_id VARCHAR(160) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  bug_cost INTEGER,
  min_bet INTEGER,
  rake_rate NUMERIC(8, 4),
  policy_version VARCHAR(128) NOT NULL,
  policy_source VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_economy_ledger_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_economy_ledger_user_created
  ON economy_ledger (user_id, created_at DESC, ledger_id DESC);
CREATE INDEX idx_economy_ledger_user_entry_created
  ON economy_ledger (user_id, entry_type, created_at DESC, ledger_id DESC);

CREATE TABLE economy_daily_claim (
  claim_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  trading_day DATE NOT NULL,
  amount INTEGER NOT NULL,
  balance_after BIGINT NOT NULL,
  policy_version VARCHAR(128) NOT NULL,
  policy_source VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_economy_daily_claim_user_day UNIQUE (user_id, trading_day)
);

CREATE INDEX idx_economy_daily_claim_user_trading_day
  ON economy_daily_claim (user_id, trading_day DESC);

CREATE TABLE game_type_config (
  game_type_key VARCHAR(64) PRIMARY KEY,
  name_zh VARCHAR(128) NOT NULL,
  name_en VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  icon VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  bug_formula VARCHAR(64) NOT NULL,
  bug_formula_params TEXT,
  max_score_per_second NUMERIC(10, 4),
  min_duration_ms INTEGER,
  daily_play_limit INTEGER,
  ui_meta TEXT,
  sort_order INTEGER,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_game_type_config_status_sort
  ON game_type_config (status, sort_order, game_type_key);
CREATE INDEX idx_game_type_config_sort
  ON game_type_config (sort_order, game_type_key);

CREATE TABLE game_session (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  game_type VARCHAR(64) NOT NULL,
  score INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL,
  bug_earned INTEGER NOT NULL,
  balance_after BIGINT NOT NULL,
  client_session_id VARCHAR(128),
  extra_data TEXT,
  validation_status VARCHAR(32) NOT NULL,
  validation_reason VARCHAR(160),
  idempotency_key VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_game_session_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_game_session_user_game_created
  ON game_session (user_id, game_type, created_at);
CREATE INDEX idx_game_session_user_validation_created
  ON game_session (user_id, validation_status, created_at);

CREATE TABLE bet_house_config (
  specimen_id VARCHAR(64) PRIMARY KEY,
  house_budget BIGINT NOT NULL,
  weight_up NUMERIC(8, 4) NOT NULL,
  weight_flat NUMERIC(8, 4) NOT NULL,
  weight_down NUMERIC(8, 4) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE bet_pool (
  specimen_id VARCHAR(64) NOT NULL,
  date DATE NOT NULL,
  pool_up BIGINT NOT NULL,
  pool_flat BIGINT NOT NULL,
  pool_down BIGINT NOT NULL,
  house_up BIGINT NOT NULL,
  house_flat BIGINT NOT NULL,
  house_down BIGINT NOT NULL,
  rake_rate NUMERIC(8, 4) NOT NULL,
  status VARCHAR(16) NOT NULL,
  bet_cutoff_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (specimen_id, date)
);

CREATE INDEX idx_bet_pool_date_status_specimen
  ON bet_pool (date, status, specimen_id);
CREATE INDEX idx_bet_pool_date_status_cutoff
  ON bet_pool (date, status, bet_cutoff_at);

CREATE TABLE bet_order (
  order_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  specimen_id VARCHAR(64) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  amount INTEGER NOT NULL,
  odds_at_place NUMERIC(12, 4) NOT NULL,
  settle_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL,
  payout BIGINT,
  moon_doom_bonus BIGINT,
  settled_at TIMESTAMPTZ,
  is_house BOOLEAN NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint VARCHAR(256) NOT NULL,
  wallet_balance_after BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_bet_order_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_bet_order_user_created
  ON bet_order (user_id, is_house, created_at DESC, order_id DESC);
CREATE INDEX idx_bet_order_user_settle_status_created
  ON bet_order (user_id, is_house, settle_date, status, created_at DESC, order_id DESC);
CREATE INDEX idx_bet_order_user_settle_status_settled
  ON bet_order (user_id, is_house, settle_date, status, settled_at DESC, order_id DESC);
CREATE INDEX idx_bet_order_specimen_settle_status
  ON bet_order (specimen_id, settle_date, status, created_at, order_id);
CREATE INDEX idx_bet_order_specimen_settle_house_user
  ON bet_order (specimen_id, settle_date, is_house, user_id);

-- =========================
-- comments
-- =========================
CREATE TABLE comment (
  comment_id VARCHAR(64) PRIMARY KEY,
  specimen_id VARCHAR(64) NOT NULL,
  author_user_id VARCHAR(64) NOT NULL,
  reply_to_comment_id VARCHAR(64),
  reply_to_user_id VARCHAR(64),
  to_username_snapshot VARCHAR(64),
  content_md TEXT NOT NULL,
  content_preview VARCHAR(200) NOT NULL,
  stamp_codes TEXT NOT NULL,
  mentioned_user_ids TEXT NOT NULL,
  resonance_count INTEGER NOT NULL CHECK (resonance_count >= 0),
  hot_score NUMERIC(19, 6) NOT NULL,
  is_chief_conclusion BOOLEAN NOT NULL,
  author_repo_role_snapshot VARCHAR(32) NOT NULL,
  moderation_risk_level VARCHAR(32) NOT NULL,
  moderation_reason_code VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  billing_ledger_id VARCHAR(64),
  deleted_by VARCHAR(64),
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_comment_specimen_new
  ON comment (specimen_id, status, created_at DESC);
CREATE INDEX idx_comment_specimen_hot
  ON comment (specimen_id, status, hot_score DESC, created_at DESC);
CREATE INDEX idx_comment_author_status_created
  ON comment (author_user_id, status, created_at DESC);
CREATE INDEX idx_comment_author_created
  ON comment (author_user_id, created_at DESC);
CREATE INDEX idx_comment_status_created
  ON comment (status, created_at DESC);
CREATE INDEX idx_comment_reply_to_comment
  ON comment (reply_to_comment_id);

CREATE TABLE comment_resonance (
  id VARCHAR(64) PRIMARY KEY,
  comment_id VARCHAR(64) NOT NULL,
  specimen_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_comment_resonance_comment_user UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_resonance_specimen_created
  ON comment_resonance (specimen_id, created_at DESC);
CREATE INDEX idx_comment_resonance_comment
  ON comment_resonance (comment_id);

CREATE TABLE comment_request_idempotency (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  client_request_id VARCHAR(128) NOT NULL,
  comment_id VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_comment_request_idempotency_user_request UNIQUE (user_id, client_request_id)
);

CREATE INDEX idx_comment_request_idempotency_created
  ON comment_request_idempotency (created_at);

CREATE TABLE comment_moderation_log (
  id VARCHAR(64) PRIMARY KEY,
  comment_id VARCHAR(64) NOT NULL,
  specimen_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  action VARCHAR(16) NOT NULL,
  reason_code VARCHAR(64),
  actor_type VARCHAR(16) NOT NULL,
  actor_id VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_comment_moderation_log_comment_created
  ON comment_moderation_log (comment_id, created_at DESC);
CREATE INDEX idx_comment_moderation_log_specimen_created
  ON comment_moderation_log (specimen_id, created_at DESC);

CREATE TABLE comment_report (
  id VARCHAR(64) PRIMARY KEY,
  comment_id VARCHAR(64) NOT NULL,
  specimen_id VARCHAR(64) NOT NULL,
  reporter_user_id VARCHAR(64) NOT NULL,
  reason_code VARCHAR(32) NOT NULL,
  message VARCHAR(1000),
  ticket_id VARCHAR(64),
  date_bucket DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_comment_report_comment_user_reason_bucket UNIQUE (
    comment_id,
    reporter_user_id,
    reason_code,
    date_bucket
  )
);

CREATE INDEX idx_comment_report_ticket
  ON comment_report (ticket_id);
CREATE INDEX idx_comment_report_specimen_created
  ON comment_report (specimen_id, created_at DESC);

-- =========================
-- notifications
-- =========================
CREATE TABLE user_notification (
  id BIGSERIAL PRIMARY KEY,
  notification_uid VARCHAR(64) NOT NULL,
  receiver_user_id VARCHAR(64) NOT NULL,
  type VARCHAR(40) NOT NULL,
  dedupe_key VARCHAR(200) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body TEXT,
  aggregate_count INTEGER NOT NULL CHECK (aggregate_count >= 1),
  actor_user_id VARCHAR(64),
  actor_nickname VARCHAR(60),
  target_url VARCHAR(500),
  fallback_url VARCHAR(500),
  status VARCHAR(10) NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_user_notification_uid UNIQUE (notification_uid),
  CONSTRAINT uk_notification_dedupe UNIQUE (receiver_user_id, type, dedupe_key)
);

CREATE INDEX idx_notification_receiver_status_created
  ON user_notification (receiver_user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_notification_receiver_type_created
  ON user_notification (receiver_user_id, type, created_at DESC, id DESC);
CREATE INDEX idx_notification_created_at
  ON user_notification (created_at);

CREATE TABLE system_broadcast (
  id BIGSERIAL PRIMARY KEY,
  broadcast_uid VARCHAR(64) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body TEXT,
  target_url VARCHAR(500),
  total_recipients INTEGER NOT NULL,
  delivered_count INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  CONSTRAINT uk_system_broadcast_uid UNIQUE (broadcast_uid)
);

CREATE INDEX idx_system_broadcast_status_created
  ON system_broadcast (status, created_at, id);
CREATE INDEX idx_system_broadcast_created
  ON system_broadcast (created_at);

CREATE TABLE user_broadcast_checkpoint (
  user_id VARCHAR(64) PRIMARY KEY,
  last_broadcast_at TIMESTAMPTZ,
  last_broadcast_id BIGINT,
  updated_at TIMESTAMPTZ NOT NULL
);

-- =========================
-- achievements (MVP subset)
-- =========================
CREATE TABLE achievement_inbox_event (
  event_id VARCHAR(64) PRIMARY KEY,
  event_type VARCHAR(128) NOT NULL,
  event_key VARCHAR(192),
  aggregate_type VARCHAR(64),
  aggregate_id VARCHAR(128),
  payload_json TEXT NOT NULL,
  occurred_at TIMESTAMPTZ,
  received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_achievement_inbox_event_type_aggregate_received
  ON achievement_inbox_event (event_type, aggregate_id, received_at DESC);
CREATE INDEX idx_achievement_inbox_event_received
  ON achievement_inbox_event (received_at DESC);

CREATE TABLE user_achievement (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  achievement_code VARCHAR(64) NOT NULL,
  unlock_source_event_id VARCHAR(64),
  unlock_context TEXT,
  reward_bug INTEGER NOT NULL,
  unlocked_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_user_achievement_user_code UNIQUE (user_id, achievement_code)
);

CREATE INDEX idx_user_achievement_user
  ON user_achievement (user_id);
CREATE INDEX idx_user_achievement_code
  ON user_achievement (achievement_code);
CREATE INDEX idx_user_achievement_user_unlocked
  ON user_achievement (user_id, unlocked_at DESC, id DESC);

-- =========================
-- narrator (MVP subset)
-- =========================
CREATE TABLE narrator_preference (
  user_id VARCHAR(64) PRIMARY KEY,
  mode VARCHAR(16) NOT NULL,
  mode_is_explicit BOOLEAN NOT NULL,
  tone_preference VARCHAR(16) NOT NULL,
  tone_is_explicit BOOLEAN NOT NULL,
  eye_follow_enabled BOOLEAN NOT NULL,
  ticker_enabled BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

-- =========================
-- shared outbox
-- =========================
CREATE TABLE outbox_event (
  event_id VARCHAR(64) PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_key VARCHAR(192) NOT NULL,
  payload_json TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempt_count INTEGER NOT NULL,
  last_error VARCHAR(512),
  occurred_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  CONSTRAINT uk_outbox_event_key UNIQUE (event_key)
);

CREATE INDEX idx_outbox_status_created
  ON outbox_event (status, created_at);
CREATE INDEX idx_outbox_aggregate
  ON outbox_event (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_event_type_created
  ON outbox_event (event_type, created_at DESC, event_id DESC);

CREATE TABLE outbox_consumer_event (
  id BIGSERIAL PRIMARY KEY,
  consumer_group VARCHAR(128) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_key VARCHAR(192) NOT NULL,
  stream_record_id VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_outbox_consumer_event_group_event UNIQUE (consumer_group, event_id)
);

CREATE INDEX idx_outbox_consumer_event_processed
  ON outbox_consumer_event (processed_at);

-- =========================
-- comments metadata
-- =========================
COMMENT ON TABLE auth_user IS 'Stores data for auth user.';
COMMENT ON COLUMN auth_user.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN auth_user.username IS 'Username displayed in product experiences.';
COMMENT ON COLUMN auth_user.username_changed IS 'Whether the username differs from initial default.';
COMMENT ON COLUMN auth_user.bug_balance IS 'Current bug token balance for this user.';
COMMENT ON COLUMN auth_user.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN auth_user.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE auth_identity IS 'Stores data for auth identity.';
COMMENT ON COLUMN auth_identity.id IS 'Primary key of this record.';
COMMENT ON COLUMN auth_identity.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN auth_identity.provider IS 'OAuth or identity provider name.';
COMMENT ON COLUMN auth_identity.provider_subject IS 'Provider-side unique subject identifier.';
COMMENT ON COLUMN auth_identity.linked_at IS 'Timestamp when this identity was linked.';

COMMENT ON TABLE auth_oauth_state IS 'Stores data for auth oauth state.';
COMMENT ON COLUMN auth_oauth_state.state IS 'Stores state.';
COMMENT ON COLUMN auth_oauth_state.consumed_at IS 'Timestamp when this state token was consumed.';

COMMENT ON TABLE auth_exchange_idempotency IS 'Stores data for auth exchange idempotency.';
COMMENT ON COLUMN auth_exchange_idempotency.idempotency_key IS 'Idempotency key used to deduplicate repeated requests.';
COMMENT ON COLUMN auth_exchange_idempotency.request_fingerprint IS 'Request fingerprint used to validate idempotent replay.';
COMMENT ON COLUMN auth_exchange_idempotency.access_token IS 'OAuth access token issued by provider.';
COMMENT ON COLUMN auth_exchange_idempotency.token_expires_in IS 'Access token lifetime in seconds.';
COMMENT ON COLUMN auth_exchange_idempotency.token_expires_at IS 'Timestamp when access token expires.';
COMMENT ON COLUMN auth_exchange_idempotency.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN auth_exchange_idempotency.username IS 'Username displayed in product experiences.';
COMMENT ON COLUMN auth_exchange_idempotency.username_changed IS 'Whether the username differs from initial default.';
COMMENT ON COLUMN auth_exchange_idempotency.bug_balance IS 'Current bug token balance for this user.';
COMMENT ON COLUMN auth_exchange_idempotency.is_new_user IS 'Boolean flag indicating whether new user is true.';
COMMENT ON COLUMN auth_exchange_idempotency.initial_bug_grant IS 'Initial bug token grant for new users.';
COMMENT ON COLUMN auth_exchange_idempotency.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE admin_user_role IS 'Stores data for admin user role.';
COMMENT ON COLUMN admin_user_role.id IS 'Primary key of this record.';
COMMENT ON COLUMN admin_user_role.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN admin_user_role.role IS 'Stores role.';
COMMENT ON COLUMN admin_user_role.granted_by IS 'Identifier of the actor in granted role.';
COMMENT ON COLUMN admin_user_role.granted_at IS 'Stores granted at.';
COMMENT ON COLUMN admin_user_role.revoked_at IS 'Stores revoked at.';
COMMENT ON COLUMN admin_user_role.is_active IS 'Boolean flag indicating whether active is true.';

COMMENT ON TABLE admin_bootstrap_record IS 'Stores data for admin bootstrap record.';
COMMENT ON COLUMN admin_bootstrap_record.id IS 'Primary key of this record.';
COMMENT ON COLUMN admin_bootstrap_record.admin_user_id IS 'Identifier of related admin user.';
COMMENT ON COLUMN admin_bootstrap_record.email_used IS 'Stores email used.';
COMMENT ON COLUMN admin_bootstrap_record.bootstrapped_at IS 'Stores bootstrapped at.';
COMMENT ON COLUMN admin_bootstrap_record.ip_address IS 'Client IP address captured during this operation.';
COMMENT ON COLUMN admin_bootstrap_record.user_agent IS 'Client user-agent string captured during this operation.';

COMMENT ON TABLE admin_audit_log IS 'Stores data for admin audit log.';
COMMENT ON COLUMN admin_audit_log.id IS 'Primary key of this record.';
COMMENT ON COLUMN admin_audit_log.operator_id IS 'Identifier of related operator.';
COMMENT ON COLUMN admin_audit_log.action IS 'Administrative action name that was performed.';
COMMENT ON COLUMN admin_audit_log.target_type IS 'Target resource type of this operation.';
COMMENT ON COLUMN admin_audit_log.target_id IS 'Identifier of related target.';
COMMENT ON COLUMN admin_audit_log.before_snapshot IS 'Serialized state snapshot before the operation.';
COMMENT ON COLUMN admin_audit_log.after_snapshot IS 'Serialized state snapshot after the operation.';
COMMENT ON COLUMN admin_audit_log.metadata IS 'Additional metadata in text format.';
COMMENT ON COLUMN admin_audit_log.request_id IS 'Identifier of related request.';
COMMENT ON COLUMN admin_audit_log.ip_address IS 'Client IP address captured during this operation.';
COMMENT ON COLUMN admin_audit_log.user_agent IS 'Client user-agent string captured during this operation.';
COMMENT ON COLUMN admin_audit_log.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE safety_ticket IS 'Stores data for safety ticket.';
COMMENT ON COLUMN safety_ticket.id IS 'Primary key of this record.';
COMMENT ON COLUMN safety_ticket.source IS 'Source channel that produced this record.';
COMMENT ON COLUMN safety_ticket.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN safety_ticket.reporter_id IS 'Identifier of related reporter.';
COMMENT ON COLUMN safety_ticket.target_type IS 'Target resource type of this operation.';
COMMENT ON COLUMN safety_ticket.target_id IS 'Identifier of related target.';
COMMENT ON COLUMN safety_ticket.reason IS 'Human-readable reason for this record.';
COMMENT ON COLUMN safety_ticket.resolution IS 'Stores resolution.';
COMMENT ON COLUMN safety_ticket.resolved_by IS 'Identifier of the actor in resolved role.';
COMMENT ON COLUMN safety_ticket.resolved_at IS 'Stores resolved at.';
COMMENT ON COLUMN safety_ticket.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN safety_ticket.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE user_ban_record IS 'Stores data for user ban record.';
COMMENT ON COLUMN user_ban_record.id IS 'Primary key of this record.';
COMMENT ON COLUMN user_ban_record.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN user_ban_record.ban_type IS 'Type of user ban policy applied.';
COMMENT ON COLUMN user_ban_record.reason IS 'Human-readable reason for this record.';
COMMENT ON COLUMN user_ban_record.banned_by IS 'Identifier of the actor in banned role.';
COMMENT ON COLUMN user_ban_record.banned_at IS 'Timestamp when the ban was activated.';
COMMENT ON COLUMN user_ban_record.expires_at IS 'Timestamp when the ban expires, if any.';
COMMENT ON COLUMN user_ban_record.unbanned_by IS 'Identifier of the actor in unbanned role.';
COMMENT ON COLUMN user_ban_record.unbanned_at IS 'Timestamp when the ban was lifted.';
COMMENT ON COLUMN user_ban_record.is_active IS 'Boolean flag indicating whether active is true.';

COMMENT ON TABLE notification_alert_task IS 'Stores data for notification alert task.';
COMMENT ON COLUMN notification_alert_task.id IS 'Primary key of this record.';
COMMENT ON COLUMN notification_alert_task.alert_type IS 'Stores alert type.';
COMMENT ON COLUMN notification_alert_task.severity IS 'Severity level of this alert.';
COMMENT ON COLUMN notification_alert_task.target_type IS 'Target resource type of this operation.';
COMMENT ON COLUMN notification_alert_task.target_id IS 'Identifier of related target.';
COMMENT ON COLUMN notification_alert_task.message IS 'Message content stored for this record.';
COMMENT ON COLUMN notification_alert_task.email_sent_to IS 'Email recipient list used for alert notification.';
COMMENT ON COLUMN notification_alert_task.email_sent_at IS 'Timestamp when email alert was sent.';
COMMENT ON COLUMN notification_alert_task.is_acknowledged IS 'Boolean flag indicating whether acknowledged is true.';
COMMENT ON COLUMN notification_alert_task.acknowledged_by IS 'Identifier of the actor in acknowledged role.';
COMMENT ON COLUMN notification_alert_task.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE specimen IS 'Stores data for specimen.';
COMMENT ON COLUMN specimen.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen.repo_full_name IS 'Full repository name in owner/name format.';
COMMENT ON COLUMN specimen.github_url IS 'URL value for github url.';
COMMENT ON COLUMN specimen.note IS 'Optional note text provided by reviewers.';
COMMENT ON COLUMN specimen.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN specimen.reviewed_by IS 'Identifier of the actor in reviewed role.';
COMMENT ON COLUMN specimen.reviewed_at IS 'Stores reviewed at.';
COMMENT ON COLUMN specimen.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN specimen.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_github_metadata IS 'Stores data for specimen github metadata.';
COMMENT ON COLUMN specimen_github_metadata.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_github_metadata.repo_id IS 'Identifier of related repo.';
COMMENT ON COLUMN specimen_github_metadata.repo_html_url IS 'URL value for repo html url.';
COMMENT ON COLUMN specimen_github_metadata.owner_login IS 'Login name of the repository owner.';
COMMENT ON COLUMN specimen_github_metadata.owner_id IS 'Identifier of related owner.';
COMMENT ON COLUMN specimen_github_metadata.owner_avatar_url IS 'URL value for owner avatar url.';
COMMENT ON COLUMN specimen_github_metadata.owner_html_url IS 'URL value for owner html url.';
COMMENT ON COLUMN specimen_github_metadata.description IS 'Description text for this entity.';
COMMENT ON COLUMN specimen_github_metadata.homepage IS 'Project homepage URL from source platform.';
COMMENT ON COLUMN specimen_github_metadata.default_branch IS 'Default branch name of the repository.';
COMMENT ON COLUMN specimen_github_metadata.languages_json IS 'Serialized JSON content for languages json.';
COMMENT ON COLUMN specimen_github_metadata.topics_json IS 'Serialized JSON content for topics json.';
COMMENT ON COLUMN specimen_github_metadata.license_spdx_id IS 'Identifier of related license spdx.';
COMMENT ON COLUMN specimen_github_metadata.license_name IS 'Human-readable repository license name.';
COMMENT ON COLUMN specimen_github_metadata.visibility IS 'Visibility level reported by source platform.';
COMMENT ON COLUMN specimen_github_metadata.archived IS 'Stores archived.';
COMMENT ON COLUMN specimen_github_metadata.fork IS 'Stores fork.';
COMMENT ON COLUMN specimen_github_metadata.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN specimen_github_metadata.updated_at_remote IS 'Stores updated at remote.';
COMMENT ON COLUMN specimen_github_metadata.stargazers_count IS 'Counter value for stargazers count.';
COMMENT ON COLUMN specimen_github_metadata.forks_count IS 'Counter value for forks count.';
COMMENT ON COLUMN specimen_github_metadata.open_issues_count IS 'Counter value for open issues count.';
COMMENT ON COLUMN specimen_github_metadata.pushed_at IS 'Timestamp of the latest source push on GitHub.';
COMMENT ON COLUMN specimen_github_metadata.metadata_synced_at IS 'Timestamp when metadata sync finished.';
COMMENT ON COLUMN specimen_github_metadata.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_arena_metrics IS 'Stores data for specimen arena metrics.';
COMMENT ON COLUMN specimen_arena_metrics.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_arena_metrics.elo IS 'Current Elo value.';
COMMENT ON COLUMN specimen_arena_metrics.hype IS 'Current hype score.';
COMMENT ON COLUMN specimen_arena_metrics.votes IS 'Stores votes.';
COMMENT ON COLUMN specimen_arena_metrics.delta_24h IS 'Stores delta 24h.';
COMMENT ON COLUMN specimen_arena_metrics.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_community_metrics IS 'Stores data for specimen community metrics.';
COMMENT ON COLUMN specimen_community_metrics.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_community_metrics.comment_count IS 'Counter value for comment count.';
COMMENT ON COLUMN specimen_community_metrics.top_roast_comment_id IS 'Identifier of related top roast comment.';
COMMENT ON COLUMN specimen_community_metrics.top_roast_resonance_count IS 'Counter value for top roast resonance count.';
COMMENT ON COLUMN specimen_community_metrics.top_roast_chief_conclusion IS 'Stores top roast chief conclusion.';
COMMENT ON COLUMN specimen_community_metrics.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_official_commentary IS 'Stores data for specimen official commentary.';
COMMENT ON COLUMN specimen_official_commentary.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_official_commentary.one_liner_zh IS 'Stores one liner zh.';
COMMENT ON COLUMN specimen_official_commentary.one_liner_en IS 'Stores one liner en.';
COMMENT ON COLUMN specimen_official_commentary.arena_reason_zh IS 'Stores arena reason zh.';
COMMENT ON COLUMN specimen_official_commentary.arena_reason_en IS 'Stores arena reason en.';
COMMENT ON COLUMN specimen_official_commentary.updated_by IS 'Identifier of the actor in updated role.';
COMMENT ON COLUMN specimen_official_commentary.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_readme_excerpt IS 'Stores data for specimen readme excerpt.';
COMMENT ON COLUMN specimen_readme_excerpt.excerpt_id IS 'Identifier of related excerpt.';
COMMENT ON COLUMN specimen_readme_excerpt.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_readme_excerpt.excerpt_type IS 'Type classification of README excerpt.';
COMMENT ON COLUMN specimen_readme_excerpt.text IS 'Text content.';
COMMENT ON COLUMN specimen_readme_excerpt.translated_text_zh IS 'Optional platform-translated Chinese text for this excerpt.';
COMMENT ON COLUMN specimen_readme_excerpt.translation_meta_json IS 'Serialized JSON metadata for translation source, translator, and timestamp.';
COMMENT ON COLUMN specimen_readme_excerpt.candidate_id IS 'Identifier of related candidate.';
COMMENT ON COLUMN specimen_readme_excerpt.priority IS 'Priority value used for ordering.';
COMMENT ON COLUMN specimen_readme_excerpt.curated_by IS 'Identifier of the actor in curated role.';
COMMENT ON COLUMN specimen_readme_excerpt.curated_at IS 'Timestamp when curation was completed.';

COMMENT ON TABLE specimen_code_highlight IS 'Stores data for specimen code highlight.';
COMMENT ON COLUMN specimen_code_highlight.highlight_id IS 'Identifier of related highlight.';
COMMENT ON COLUMN specimen_code_highlight.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_code_highlight.title IS 'Display title text.';
COMMENT ON COLUMN specimen_code_highlight.code_language IS 'Programming language of highlighted snippet.';
COMMENT ON COLUMN specimen_code_highlight.snippet IS 'Highlighted code snippet content.';
COMMENT ON COLUMN specimen_code_highlight.explain_text IS 'Explanation text for the code snippet.';
COMMENT ON COLUMN specimen_code_highlight.candidate_id IS 'Identifier of related candidate.';
COMMENT ON COLUMN specimen_code_highlight.priority IS 'Priority value used for ordering.';
COMMENT ON COLUMN specimen_code_highlight.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN specimen_code_highlight.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_repo_identity IS 'Stores data for specimen repo identity.';
COMMENT ON COLUMN specimen_repo_identity.id IS 'Primary key of this record.';
COMMENT ON COLUMN specimen_repo_identity.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_repo_identity.github_user_id IS 'Identifier of related github user.';
COMMENT ON COLUMN specimen_repo_identity.github_login IS 'Stores github login.';
COMMENT ON COLUMN specimen_repo_identity.github_avatar_url IS 'URL value for github avatar url.';
COMMENT ON COLUMN specimen_repo_identity.github_html_url IS 'URL value for github html url.';
COMMENT ON COLUMN specimen_repo_identity.role IS 'Stores role.';
COMMENT ON COLUMN specimen_repo_identity.source IS 'Source channel that produced this record.';
COMMENT ON COLUMN specimen_repo_identity.active IS 'Whether this identity binding is currently active.';
COMMENT ON COLUMN specimen_repo_identity.contributions IS 'Approximate contribution count from source data.';
COMMENT ON COLUMN specimen_repo_identity.synced_at IS 'Timestamp when this record was synchronized.';

COMMENT ON TABLE tag_dimension IS 'Stores data for tag dimension.';
COMMENT ON COLUMN tag_dimension.dimension_key IS 'Dimension key used for taxonomy grouping.';
COMMENT ON COLUMN tag_dimension.name_zh IS 'Chinese display name.';
COMMENT ON COLUMN tag_dimension.name_en IS 'English display name.';
COMMENT ON COLUMN tag_dimension.description IS 'Description text for this entity.';
COMMENT ON COLUMN tag_dimension.select_mode IS 'Selection mode rules for this dimension.';
COMMENT ON COLUMN tag_dimension.min_select IS 'Minimum selectable tags for this dimension.';
COMMENT ON COLUMN tag_dimension.max_select IS 'Maximum selectable tags for this dimension.';
COMMENT ON COLUMN tag_dimension.required IS 'Whether this dimension is required.';
COMMENT ON COLUMN tag_dimension.match_enabled IS 'Whether match is enabled.';
COMMENT ON COLUMN tag_dimension.default_weight IS 'Default weight used in scoring when tag weight is absent.';
COMMENT ON COLUMN tag_dimension.sort_order IS 'Sort order used for UI display.';
COMMENT ON COLUMN tag_dimension.ui_meta_json IS 'Serialized JSON content for ui meta json.';
COMMENT ON COLUMN tag_dimension.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN tag_dimension.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE tag_definition IS 'Stores data for tag definition.';
COMMENT ON COLUMN tag_definition.dimension_key IS 'Dimension key used for taxonomy grouping.';
COMMENT ON COLUMN tag_definition.tag_key IS 'Tag key used inside a dimension.';
COMMENT ON COLUMN tag_definition.name_zh IS 'Chinese display name.';
COMMENT ON COLUMN tag_definition.name_en IS 'English display name.';
COMMENT ON COLUMN tag_definition.description IS 'Description text for this entity.';
COMMENT ON COLUMN tag_definition.display_order IS 'Display order within this dimension.';
COMMENT ON COLUMN tag_definition.match_weight IS 'Weight contribution of this tag in match scoring.';
COMMENT ON COLUMN tag_definition.ui_meta_json IS 'Serialized JSON content for ui meta json.';
COMMENT ON COLUMN tag_definition.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN tag_definition.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_tag IS 'Stores data for specimen tag.';
COMMENT ON COLUMN specimen_tag.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_tag.dimension_key IS 'Dimension key used for taxonomy grouping.';
COMMENT ON COLUMN specimen_tag.tag_key IS 'Tag key used inside a dimension.';
COMMENT ON COLUMN specimen_tag.assigned_at IS 'Timestamp when this tag was assigned.';

COMMENT ON TABLE user_watchlist_item IS 'Stores data for user watchlist item.';
COMMENT ON COLUMN user_watchlist_item.item_id IS 'Identifier of related item.';
COMMENT ON COLUMN user_watchlist_item.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN user_watchlist_item.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN user_watchlist_item.source IS 'Source channel that produced this record.';
COMMENT ON COLUMN user_watchlist_item.added_at IS 'Timestamp when item was added to watchlist.';

COMMENT ON TABLE specimen_admin_action_idempotency IS 'Stores data for specimen admin action idempotency.';
COMMENT ON COLUMN specimen_admin_action_idempotency.operation_key IS 'Primary key for admin operation idempotency record.';
COMMENT ON COLUMN specimen_admin_action_idempotency.admin_user_id IS 'Identifier of related admin user.';
COMMENT ON COLUMN specimen_admin_action_idempotency.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_admin_action_idempotency.operation IS 'Admin operation type being deduplicated.';
COMMENT ON COLUMN specimen_admin_action_idempotency.idempotency_key IS 'Idempotency key used to deduplicate repeated requests.';
COMMENT ON COLUMN specimen_admin_action_idempotency.request_fingerprint IS 'Request fingerprint used to validate idempotent replay.';
COMMENT ON COLUMN specimen_admin_action_idempotency.response_status IS 'Stored response status for idempotent replay.';
COMMENT ON COLUMN specimen_admin_action_idempotency.response_reviewed_by IS 'Identifier of the actor in response reviewed role.';
COMMENT ON COLUMN specimen_admin_action_idempotency.response_reviewed_at IS 'Reviewer timestamp returned in stored response.';
COMMENT ON COLUMN specimen_admin_action_idempotency.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE specimen_rating IS 'Stores data for specimen rating.';
COMMENT ON COLUMN specimen_rating.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN specimen_rating.elo_score IS 'Current Elo score.';
COMMENT ON COLUMN specimen_rating.calibrated_score IS 'Calibrated score derived from ranking pipeline.';
COMMENT ON COLUMN specimen_rating.matches_played IS 'Total matches played by this specimen.';
COMMENT ON COLUMN specimen_rating.ipo_status IS 'Arena IPO lifecycle status of this specimen.';
COMMENT ON COLUMN specimen_rating.k_factor IS 'Elo K-factor used for rating updates.';
COMMENT ON COLUMN specimen_rating.elo_open_today IS 'Elo opening value of current trading day.';
COMMENT ON COLUMN specimen_rating.delta_r_7d_stddev IS 'Seven-day standard deviation of Elo delta.';
COMMENT ON COLUMN specimen_rating.recent_appearances IS 'Recent appearance count in arena matchmaking.';
COMMENT ON COLUMN specimen_rating.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE specimen_match_pair IS 'Stores data for specimen match pair.';
COMMENT ON COLUMN specimen_match_pair.left_specimen_id IS 'Identifier of related left specimen.';
COMMENT ON COLUMN specimen_match_pair.right_specimen_id IS 'Identifier of related right specimen.';
COMMENT ON COLUMN specimen_match_pair.match_type IS 'Matchmaking type used for this pair or vote.';
COMMENT ON COLUMN specimen_match_pair.match_score IS 'Computed similarity score for this pair.';
COMMENT ON COLUMN specimen_match_pair.match_profile_version IS 'Version value for match profile version.';
COMMENT ON COLUMN specimen_match_pair.computed_at IS 'Timestamp when this pair score was computed.';

COMMENT ON TABLE battle_vote IS 'Stores data for battle vote.';
COMMENT ON COLUMN battle_vote.vote_id IS 'Identifier of related vote.';
COMMENT ON COLUMN battle_vote.idempotency_key IS 'Idempotency key used to deduplicate repeated requests.';
COMMENT ON COLUMN battle_vote.request_fingerprint IS 'Request fingerprint used to validate idempotent replay.';
COMMENT ON COLUMN battle_vote.battle_id IS 'Identifier of related battle.';
COMMENT ON COLUMN battle_vote.left_specimen_id IS 'Identifier of related left specimen.';
COMMENT ON COLUMN battle_vote.right_specimen_id IS 'Identifier of related right specimen.';
COMMENT ON COLUMN battle_vote.winner IS 'Winning side selected by the voter.';
COMMENT ON COLUMN battle_vote.voter_id IS 'Identifier of related voter.';
COMMENT ON COLUMN battle_vote.bug_cost IS 'Bug token cost charged for this operation.';
COMMENT ON COLUMN battle_vote.left_elo_before IS 'Left specimen Elo before rating update.';
COMMENT ON COLUMN battle_vote.left_elo_delta IS 'Elo delta applied to the left specimen.';
COMMENT ON COLUMN battle_vote.right_elo_before IS 'Right specimen Elo before rating update.';
COMMENT ON COLUMN battle_vote.right_elo_delta IS 'Elo delta applied to the right specimen.';
COMMENT ON COLUMN battle_vote.left_elo_after IS 'Left specimen Elo after rating update.';
COMMENT ON COLUMN battle_vote.right_elo_after IS 'Right specimen Elo after rating update.';
COMMENT ON COLUMN battle_vote.left_k_factor IS 'K-factor used for left specimen Elo calculation.';
COMMENT ON COLUMN battle_vote.right_k_factor IS 'K-factor used for right specimen Elo calculation.';
COMMENT ON COLUMN battle_vote.match_type IS 'Matchmaking type used for this pair or vote.';
COMMENT ON COLUMN battle_vote.match_profile_version IS 'Version value for match profile version.';
COMMENT ON COLUMN battle_vote.left_phase IS 'Lifecycle phase of left specimen during the battle.';
COMMENT ON COLUMN battle_vote.right_phase IS 'Lifecycle phase of right specimen during the battle.';
COMMENT ON COLUMN battle_vote.wallet_balance_after IS 'Wallet balance snapshot right after this operation.';
COMMENT ON COLUMN battle_vote.policy_snapshot IS 'Serialized policy snapshot used during execution.';
COMMENT ON COLUMN battle_vote.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE elo_daily_snapshot IS 'Stores data for elo daily snapshot.';
COMMENT ON COLUMN elo_daily_snapshot.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN elo_daily_snapshot.date IS 'Date partition key for daily records.';
COMMENT ON COLUMN elo_daily_snapshot.elo_open IS 'Elo value at daily open.';
COMMENT ON COLUMN elo_daily_snapshot.elo_close IS 'Elo value at daily close.';
COMMENT ON COLUMN elo_daily_snapshot.delta_r IS 'Daily Elo delta value.';
COMMENT ON COLUMN elo_daily_snapshot.matches_count IS 'Counter value for matches count.';
COMMENT ON COLUMN elo_daily_snapshot.both_bad_count IS 'Counter value for both bad count.';
COMMENT ON COLUMN elo_daily_snapshot.global_correction IS 'Global correction applied during daily recompute.';

COMMENT ON TABLE economy_wallet IS 'Stores data for economy wallet.';
COMMENT ON COLUMN economy_wallet.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN economy_wallet.balance IS 'Current wallet balance.';
COMMENT ON COLUMN economy_wallet.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN economy_wallet.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE economy_ledger IS 'Stores data for economy ledger.';
COMMENT ON COLUMN economy_ledger.ledger_id IS 'Identifier of related ledger.';
COMMENT ON COLUMN economy_ledger.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN economy_ledger.entry_type IS 'Stores entry type.';
COMMENT ON COLUMN economy_ledger.delta IS 'Net delta value applied by this ledger entry.';
COMMENT ON COLUMN economy_ledger.balance_after IS 'Wallet balance after applying this operation.';
COMMENT ON COLUMN economy_ledger.ref_type IS 'Stores ref type.';
COMMENT ON COLUMN economy_ledger.ref_id IS 'Identifier of related ref.';
COMMENT ON COLUMN economy_ledger.idempotency_key IS 'Idempotency key used to deduplicate repeated requests.';
COMMENT ON COLUMN economy_ledger.bug_cost IS 'Bug token cost charged for this operation.';
COMMENT ON COLUMN economy_ledger.min_bet IS 'Minimum bet rule snapshot for this operation.';
COMMENT ON COLUMN economy_ledger.rake_rate IS 'Rake rate used in pool and settlement calculations.';
COMMENT ON COLUMN economy_ledger.policy_version IS 'Version value for policy version.';
COMMENT ON COLUMN economy_ledger.policy_source IS 'Source of policy configuration used for this operation.';
COMMENT ON COLUMN economy_ledger.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE economy_daily_claim IS 'Stores data for economy daily claim.';
COMMENT ON COLUMN economy_daily_claim.claim_id IS 'Identifier of related claim.';
COMMENT ON COLUMN economy_daily_claim.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN economy_daily_claim.trading_day IS 'Trading day associated with this record.';
COMMENT ON COLUMN economy_daily_claim.amount IS 'Amount involved in this transaction.';
COMMENT ON COLUMN economy_daily_claim.balance_after IS 'Wallet balance after applying this operation.';
COMMENT ON COLUMN economy_daily_claim.policy_version IS 'Version value for policy version.';
COMMENT ON COLUMN economy_daily_claim.policy_source IS 'Source of policy configuration used for this operation.';
COMMENT ON COLUMN economy_daily_claim.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE game_type_config IS 'Stores data for game type config.';
COMMENT ON COLUMN game_type_config.game_type_key IS 'Stable key of game type configuration.';
COMMENT ON COLUMN game_type_config.name_zh IS 'Chinese display name.';
COMMENT ON COLUMN game_type_config.name_en IS 'English display name.';
COMMENT ON COLUMN game_type_config.description IS 'Description text for this entity.';
COMMENT ON COLUMN game_type_config.icon IS 'Icon identifier used by UI.';
COMMENT ON COLUMN game_type_config.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN game_type_config.bug_formula IS 'Formula identifier used to calculate bug rewards.';
COMMENT ON COLUMN game_type_config.bug_formula_params IS 'Serialized parameters for bug reward formula.';
COMMENT ON COLUMN game_type_config.max_score_per_second IS 'Upper bound score growth allowed per second.';
COMMENT ON COLUMN game_type_config.min_duration_ms IS 'Minimum required duration in milliseconds.';
COMMENT ON COLUMN game_type_config.daily_play_limit IS 'Maximum allowed plays per user per day.';
COMMENT ON COLUMN game_type_config.ui_meta IS 'UI metadata in serialized text format.';
COMMENT ON COLUMN game_type_config.sort_order IS 'Sort order used for UI display.';
COMMENT ON COLUMN game_type_config.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN game_type_config.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE game_session IS 'Stores data for game session.';
COMMENT ON COLUMN game_session.id IS 'Primary key of this record.';
COMMENT ON COLUMN game_session.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN game_session.game_type IS 'Game type key submitted by client.';
COMMENT ON COLUMN game_session.score IS 'Score value submitted for this session.';
COMMENT ON COLUMN game_session.duration_ms IS 'Duration of the session in milliseconds.';
COMMENT ON COLUMN game_session.bug_earned IS 'Bug token amount earned in this session.';
COMMENT ON COLUMN game_session.balance_after IS 'Wallet balance after applying this operation.';
COMMENT ON COLUMN game_session.client_session_id IS 'Identifier of related client session.';
COMMENT ON COLUMN game_session.extra_data IS 'Additional serialized data provided by client.';
COMMENT ON COLUMN game_session.validation_status IS 'Validation result status for this session.';
COMMENT ON COLUMN game_session.validation_reason IS 'Validation reason detail when rejected.';
COMMENT ON COLUMN game_session.idempotency_key IS 'Idempotency key used to deduplicate repeated requests.';
COMMENT ON COLUMN game_session.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE bet_house_config IS 'Stores data for bet house config.';
COMMENT ON COLUMN bet_house_config.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN bet_house_config.house_budget IS 'Configured budget allocated to house participation.';
COMMENT ON COLUMN bet_house_config.weight_up IS 'Configured house probability weight for UP direction.';
COMMENT ON COLUMN bet_house_config.weight_flat IS 'Configured house probability weight for FLAT direction.';
COMMENT ON COLUMN bet_house_config.weight_down IS 'Configured house probability weight for DOWN direction.';
COMMENT ON COLUMN bet_house_config.updated_by IS 'Identifier of the actor in updated role.';
COMMENT ON COLUMN bet_house_config.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN bet_house_config.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE bet_pool IS 'Stores data for bet pool.';
COMMENT ON COLUMN bet_pool.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN bet_pool.date IS 'Date partition key for daily records.';
COMMENT ON COLUMN bet_pool.pool_up IS 'Total user pool amount for UP direction.';
COMMENT ON COLUMN bet_pool.pool_flat IS 'Total user pool amount for FLAT direction.';
COMMENT ON COLUMN bet_pool.pool_down IS 'Total user pool amount for DOWN direction.';
COMMENT ON COLUMN bet_pool.house_up IS 'House contribution amount for UP direction.';
COMMENT ON COLUMN bet_pool.house_flat IS 'House contribution amount for FLAT direction.';
COMMENT ON COLUMN bet_pool.house_down IS 'House contribution amount for DOWN direction.';
COMMENT ON COLUMN bet_pool.rake_rate IS 'Rake rate used in pool and settlement calculations.';
COMMENT ON COLUMN bet_pool.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN bet_pool.bet_cutoff_at IS 'Timestamp after which new bets are rejected.';
COMMENT ON COLUMN bet_pool.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE bet_order IS 'Stores data for bet order.';
COMMENT ON COLUMN bet_order.order_id IS 'Identifier of related order.';
COMMENT ON COLUMN bet_order.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN bet_order.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN bet_order.direction IS 'Bet direction selected by the user.';
COMMENT ON COLUMN bet_order.amount IS 'Amount involved in this transaction.';
COMMENT ON COLUMN bet_order.odds_at_place IS 'Odds snapshot when this order was placed.';
COMMENT ON COLUMN bet_order.settle_date IS 'Settlement date for this order.';
COMMENT ON COLUMN bet_order.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN bet_order.payout IS 'Payout amount settled for this order.';
COMMENT ON COLUMN bet_order.moon_doom_bonus IS 'Bonus amount from moon/doom settlement rule.';
COMMENT ON COLUMN bet_order.settled_at IS 'Timestamp when settlement was completed.';
COMMENT ON COLUMN bet_order.is_house IS 'Boolean flag indicating whether house is true.';
COMMENT ON COLUMN bet_order.idempotency_key IS 'Idempotency key used to deduplicate repeated requests.';
COMMENT ON COLUMN bet_order.request_fingerprint IS 'Request fingerprint used to validate idempotent replay.';
COMMENT ON COLUMN bet_order.wallet_balance_after IS 'Wallet balance snapshot right after this operation.';
COMMENT ON COLUMN bet_order.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE comment IS 'Stores data for comment.';
COMMENT ON COLUMN comment.comment_id IS 'Identifier of related comment.';
COMMENT ON COLUMN comment.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN comment.author_user_id IS 'Identifier of related author user.';
COMMENT ON COLUMN comment.reply_to_comment_id IS 'Identifier of related reply to comment.';
COMMENT ON COLUMN comment.reply_to_user_id IS 'Identifier of related reply to user.';
COMMENT ON COLUMN comment.to_username_snapshot IS 'Username snapshot of reply target at write time.';
COMMENT ON COLUMN comment.content_md IS 'Original markdown content submitted by the user.';
COMMENT ON COLUMN comment.content_preview IS 'Short preview of the content.';
COMMENT ON COLUMN comment.stamp_codes IS 'Serialized stamp code list attached to comment.';
COMMENT ON COLUMN comment.mentioned_user_ids IS 'Serialized mentioned user identifier list.';
COMMENT ON COLUMN comment.resonance_count IS 'Counter value for resonance count.';
COMMENT ON COLUMN comment.hot_score IS 'Ranking score used for hot comment sorting.';
COMMENT ON COLUMN comment.is_chief_conclusion IS 'Boolean flag indicating whether chief conclusion is true.';
COMMENT ON COLUMN comment.author_repo_role_snapshot IS 'Repository role snapshot of comment author.';
COMMENT ON COLUMN comment.moderation_risk_level IS 'Automated moderation risk classification.';
COMMENT ON COLUMN comment.moderation_reason_code IS 'Reason code produced by moderation pipeline.';
COMMENT ON COLUMN comment.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN comment.billing_ledger_id IS 'Identifier of related billing ledger.';
COMMENT ON COLUMN comment.deleted_by IS 'Identifier of the actor in deleted role.';
COMMENT ON COLUMN comment.deleted_at IS 'Timestamp when this record was soft deleted.';
COMMENT ON COLUMN comment.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN comment.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE comment_resonance IS 'Stores data for comment resonance.';
COMMENT ON COLUMN comment_resonance.id IS 'Primary key of this record.';
COMMENT ON COLUMN comment_resonance.comment_id IS 'Identifier of related comment.';
COMMENT ON COLUMN comment_resonance.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN comment_resonance.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN comment_resonance.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE comment_request_idempotency IS 'Stores data for comment request idempotency.';
COMMENT ON COLUMN comment_request_idempotency.id IS 'Primary key of this record.';
COMMENT ON COLUMN comment_request_idempotency.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN comment_request_idempotency.client_request_id IS 'Identifier of related client request.';
COMMENT ON COLUMN comment_request_idempotency.comment_id IS 'Identifier of related comment.';
COMMENT ON COLUMN comment_request_idempotency.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE comment_moderation_log IS 'Stores data for comment moderation log.';
COMMENT ON COLUMN comment_moderation_log.id IS 'Primary key of this record.';
COMMENT ON COLUMN comment_moderation_log.comment_id IS 'Identifier of related comment.';
COMMENT ON COLUMN comment_moderation_log.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN comment_moderation_log.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN comment_moderation_log.action IS 'Administrative action name that was performed.';
COMMENT ON COLUMN comment_moderation_log.reason_code IS 'Machine-readable reason code.';
COMMENT ON COLUMN comment_moderation_log.actor_type IS 'Stores actor type.';
COMMENT ON COLUMN comment_moderation_log.actor_id IS 'Identifier of related actor.';
COMMENT ON COLUMN comment_moderation_log.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE comment_report IS 'Stores data for comment report.';
COMMENT ON COLUMN comment_report.id IS 'Primary key of this record.';
COMMENT ON COLUMN comment_report.comment_id IS 'Identifier of related comment.';
COMMENT ON COLUMN comment_report.specimen_id IS 'Identifier of related specimen.';
COMMENT ON COLUMN comment_report.reporter_user_id IS 'Identifier of related reporter user.';
COMMENT ON COLUMN comment_report.reason_code IS 'Machine-readable reason code.';
COMMENT ON COLUMN comment_report.message IS 'Message content stored for this record.';
COMMENT ON COLUMN comment_report.ticket_id IS 'Identifier of related ticket.';
COMMENT ON COLUMN comment_report.date_bucket IS 'Date bucket used for duplicate report control.';
COMMENT ON COLUMN comment_report.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE user_notification IS 'Stores data for user notification.';
COMMENT ON COLUMN user_notification.id IS 'Primary key of this record.';
COMMENT ON COLUMN user_notification.notification_uid IS 'Stable external unique identifier.';
COMMENT ON COLUMN user_notification.receiver_user_id IS 'Identifier of related receiver user.';
COMMENT ON COLUMN user_notification.type IS 'Business type classification of this record.';
COMMENT ON COLUMN user_notification.dedupe_key IS 'Deduplication key within the same receiver and type.';
COMMENT ON COLUMN user_notification.title IS 'Display title text.';
COMMENT ON COLUMN user_notification.body IS 'Body content text.';
COMMENT ON COLUMN user_notification.aggregate_count IS 'Counter value for aggregate count.';
COMMENT ON COLUMN user_notification.actor_user_id IS 'Identifier of related actor user.';
COMMENT ON COLUMN user_notification.actor_nickname IS 'Nickname snapshot of actor user.';
COMMENT ON COLUMN user_notification.target_url IS 'URL value for target url.';
COMMENT ON COLUMN user_notification.fallback_url IS 'URL value for fallback url.';
COMMENT ON COLUMN user_notification.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN user_notification.read_at IS 'Timestamp when the notification was read by the user.';
COMMENT ON COLUMN user_notification.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN user_notification.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE system_broadcast IS 'Stores data for system broadcast.';
COMMENT ON COLUMN system_broadcast.id IS 'Primary key of this record.';
COMMENT ON COLUMN system_broadcast.broadcast_uid IS 'Stable external unique identifier.';
COMMENT ON COLUMN system_broadcast.title IS 'Display title text.';
COMMENT ON COLUMN system_broadcast.body IS 'Body content text.';
COMMENT ON COLUMN system_broadcast.target_url IS 'URL value for target url.';
COMMENT ON COLUMN system_broadcast.total_recipients IS 'Stores total recipients.';
COMMENT ON COLUMN system_broadcast.delivered_count IS 'Counter value for delivered count.';
COMMENT ON COLUMN system_broadcast.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN system_broadcast.created_by IS 'Identifier of the actor in created role.';
COMMENT ON COLUMN system_broadcast.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN system_broadcast.completed_at IS 'Timestamp when this process was completed.';

COMMENT ON TABLE user_broadcast_checkpoint IS 'Stores data for user broadcast checkpoint.';
COMMENT ON COLUMN user_broadcast_checkpoint.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN user_broadcast_checkpoint.last_broadcast_at IS 'Timestamp of last processed system broadcast.';
COMMENT ON COLUMN user_broadcast_checkpoint.last_broadcast_id IS 'Identifier of related last broadcast.';
COMMENT ON COLUMN user_broadcast_checkpoint.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE achievement_inbox_event IS 'Stores data for achievement inbox event.';
COMMENT ON COLUMN achievement_inbox_event.event_id IS 'Identifier of related event.';
COMMENT ON COLUMN achievement_inbox_event.event_type IS 'Business event type name.';
COMMENT ON COLUMN achievement_inbox_event.event_key IS 'Business event key used for deduplication and idempotency.';
COMMENT ON COLUMN achievement_inbox_event.aggregate_type IS 'Aggregate type associated with this event.';
COMMENT ON COLUMN achievement_inbox_event.aggregate_id IS 'Identifier of related aggregate.';
COMMENT ON COLUMN achievement_inbox_event.payload_json IS 'Serialized JSON payload of the event.';
COMMENT ON COLUMN achievement_inbox_event.occurred_at IS 'Timestamp when the business event occurred.';
COMMENT ON COLUMN achievement_inbox_event.received_at IS 'Timestamp when the event was received by this service.';

COMMENT ON TABLE user_achievement IS 'Stores data for user achievement.';
COMMENT ON COLUMN user_achievement.id IS 'Primary key of this record.';
COMMENT ON COLUMN user_achievement.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN user_achievement.achievement_code IS 'Stable code of the unlocked achievement.';
COMMENT ON COLUMN user_achievement.unlock_source_event_id IS 'Identifier of related unlock source event.';
COMMENT ON COLUMN user_achievement.unlock_context IS 'Serialized context captured at unlock time.';
COMMENT ON COLUMN user_achievement.reward_bug IS 'Bug token reward granted by this achievement.';
COMMENT ON COLUMN user_achievement.unlocked_at IS 'Timestamp when achievement was unlocked.';
COMMENT ON COLUMN user_achievement.created_at IS 'Timestamp when this record was created.';

COMMENT ON TABLE narrator_preference IS 'Stores data for narrator preference.';
COMMENT ON COLUMN narrator_preference.user_id IS 'Identifier of related user.';
COMMENT ON COLUMN narrator_preference.mode IS 'Selected narrator mode.';
COMMENT ON COLUMN narrator_preference.mode_is_explicit IS 'Whether narrator mode was explicitly set by the user.';
COMMENT ON COLUMN narrator_preference.tone_preference IS 'Preferred narrator tone.';
COMMENT ON COLUMN narrator_preference.tone_is_explicit IS 'Whether tone preference was explicitly set by the user.';
COMMENT ON COLUMN narrator_preference.eye_follow_enabled IS 'Whether eye follow is enabled.';
COMMENT ON COLUMN narrator_preference.ticker_enabled IS 'Whether ticker is enabled.';
COMMENT ON COLUMN narrator_preference.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN narrator_preference.updated_at IS 'Timestamp when this record was last updated.';

COMMENT ON TABLE outbox_event IS 'Stores data for outbox event.';
COMMENT ON COLUMN outbox_event.event_id IS 'Identifier of related event.';
COMMENT ON COLUMN outbox_event.aggregate_type IS 'Aggregate type associated with this event.';
COMMENT ON COLUMN outbox_event.aggregate_id IS 'Identifier of related aggregate.';
COMMENT ON COLUMN outbox_event.event_type IS 'Business event type name.';
COMMENT ON COLUMN outbox_event.event_key IS 'Business event key used for deduplication and idempotency.';
COMMENT ON COLUMN outbox_event.payload_json IS 'Serialized JSON payload of the event.';
COMMENT ON COLUMN outbox_event.status IS 'Current lifecycle status of this record.';
COMMENT ON COLUMN outbox_event.attempt_count IS 'Counter value for attempt count.';
COMMENT ON COLUMN outbox_event.last_error IS 'Stores last error.';
COMMENT ON COLUMN outbox_event.occurred_at IS 'Timestamp when the business event occurred.';
COMMENT ON COLUMN outbox_event.created_at IS 'Timestamp when this record was created.';
COMMENT ON COLUMN outbox_event.published_at IS 'Timestamp when the event was published successfully.';

COMMENT ON TABLE outbox_consumer_event IS 'Stores data for outbox consumer event.';
COMMENT ON COLUMN outbox_consumer_event.id IS 'Primary key of this record.';
COMMENT ON COLUMN outbox_consumer_event.consumer_group IS 'Consumer group that processed this event.';
COMMENT ON COLUMN outbox_consumer_event.event_id IS 'Identifier of related event.';
COMMENT ON COLUMN outbox_consumer_event.event_type IS 'Business event type name.';
COMMENT ON COLUMN outbox_consumer_event.event_key IS 'Business event key used for deduplication and idempotency.';
COMMENT ON COLUMN outbox_consumer_event.stream_record_id IS 'Identifier of related stream record.';
COMMENT ON COLUMN outbox_consumer_event.processed_at IS 'Timestamp when the consumer finished processing this event.';

-- =========================
-- end comments metadata
-- =========================

