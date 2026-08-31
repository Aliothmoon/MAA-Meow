package com.aliothmoon.maameow.maa.callback

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

/**
 * 黑流树海（BlackFlow）回调里的裸 code -> 本地化文案，对齐 WPF RoguelikeSettingsUserControlModel
 *
 * 只覆盖 core 定义的有限枚举；rule/milestone 这类随策略配置增长的 id 查不到时返回 null，
 * 调用方回落到原始 id——比显示「未知」更有排查价值
 */
object BlackFlowLogText {

    @StringRes
    fun profile(code: String?): Int = when (code) {
        "investment" -> R.string.panel_roguelike_mode_desc_investment
        "burn", "burn_with_investment" -> R.string.panel_roguelike_mode_desc_exp_blackflow
        "baby_animal", "baby_animal_floor3" -> R.string.panel_roguelike_mode_blackflow_baby_animal
        else -> R.string.blackflow_strategy_unknown
    }

    @StringRes
    fun reasonCategory(code: String?): Int = when (code) {
        "mandatory_goal" -> R.string.blackflow_reason_mandatory_goal
        "resource_reserve" -> R.string.blackflow_reason_resource_reserve
        "preferred_goal" -> R.string.blackflow_reason_preferred_goal
        "development" -> R.string.blackflow_reason_development
        "risk_avoidance" -> R.string.blackflow_reason_risk_avoidance
        "safety_fallback" -> R.string.blackflow_reason_safety_fallback
        else -> R.string.blackflow_reason_tie_break
    }

    @StringRes
    fun reasonDetail(code: String?): Int = when (code) {
        "selected unclassified frontier probe" -> R.string.blackflow_decision_probe_unknown_node
        "selected by lexicographic policy order" -> R.string.blackflow_decision_policy_order
        else -> R.string.blackflow_decision_detail_unknown
    }

    @StringRes
    fun movement(code: String?): Int = when (code) {
        "walk" -> R.string.blackflow_movement_walk
        else -> R.string.blackflow_movement_processing
    }

    @StringRes
    fun warning(code: String?): Int = when (code) {
        "map_rebuild_failed" -> R.string.blackflow_warning_map_rebuild_failed
        "page_recovery_failed" -> R.string.blackflow_warning_page_recovery_failed
        "preview_cost_changed" -> R.string.blackflow_warning_preview_cost_changed
        "route_blocked" -> R.string.blackflow_warning_route_blocked
        "insufficient_action_points" -> R.string.blackflow_warning_insufficient_action_points
        "target_state_changed" -> R.string.blackflow_warning_target_state_changed
        "target_unreachable" -> R.string.blackflow_warning_target_unreachable
        "inferred_edge_selected" -> R.string.blackflow_warning_inferred_edge
        "post_move_mismatch" -> R.string.blackflow_warning_post_move_mismatch
        "identity_conflict" -> R.string.blackflow_warning_identity_conflict
        else -> R.string.blackflow_warning_unknown
    }

    @StringRes
    fun milestoneStatus(code: String?): Int = when (code) {
        "available" -> R.string.blackflow_milestone_status_available
        "satisfied" -> R.string.blackflow_milestone_status_satisfied
        "missed" -> R.string.blackflow_milestone_status_missed
        "impossible" -> R.string.blackflow_milestone_status_impossible
        else -> R.string.blackflow_milestone_status_unknown
    }

    @StringRes
    fun nodeType(code: String?): Int = when (code) {
        "empty" -> R.string.blackflow_node_empty
        "battle_normal", "combat" -> R.string.blackflow_node_combat
        "battle_elite", "emergency_combat" -> R.string.blackflow_node_emergency_combat
        "battle_boss", "boss" -> R.string.blackflow_node_boss
        "shop", "battle_shop" -> R.string.blackflow_node_battle_shop
        "scrap_shop" -> R.string.blackflow_node_scrap_shop
        "incident", "encounter" -> R.string.blackflow_node_encounter
        "hide_invisible", "mysterious_presage" -> R.string.blackflow_node_mysterious_presage
        "hide_battle", "ferocious_presage" -> R.string.blackflow_node_ferocious_presage
        "expedition", "scout" -> R.string.blackflow_node_scout
        "battle_savage", "resident_stronghold" -> R.string.blackflow_node_resident_stronghold
        "duel", "face_off" -> R.string.blackflow_node_duel
        "employ", "emergency_aid" -> R.string.blackflow_node_emergency_aid
        "rest" -> R.string.blackflow_node_rest
        "light", "feather_point" -> R.string.blackflow_node_feather_point
        "door", "winding_passage" -> R.string.blackflow_node_winding_passage
        "sacrifice" -> R.string.blackflow_node_sacrifice
        "wish" -> R.string.blackflow_node_wish
        "portal", "bosky_passage" -> R.string.blackflow_node_bosky_passage
        "final" -> R.string.blackflow_node_final
        "fate" -> R.string.blackflow_node_fate
        "evacuate" -> R.string.blackflow_node_evacuate
        "teleporter" -> R.string.blackflow_node_teleporter
        "other" -> R.string.blackflow_node_other
        else -> R.string.blackflow_node_unknown
    }

    @StringRes
    fun outcome(code: String?): Int = when (code) {
        "investment_completed" -> R.string.blackflow_outcome_investment_completed
        "investment_missed" -> R.string.blackflow_outcome_investment_missed
        "burn_completed" -> R.string.blackflow_outcome_floor3_route_completed
        "baby_cultivation_completed" -> R.string.blackflow_outcome_baby_cultivation_completed
        "baby_cultivation_target_missed" -> R.string.blackflow_outcome_baby_cultivation_target_missed
        "ending_prerequisite_failed" -> R.string.blackflow_outcome_ending_prerequisite_failed
        "strategy_completed" -> R.string.blackflow_outcome_strategy_completed
        "page_recovery_failed" -> R.string.blackflow_outcome_page_recovery_failed
        "ending2_completed" -> R.string.blackflow_outcome_ending2_completed
        "ending3_completed" -> R.string.blackflow_outcome_ending3_completed
        "ending2_prerequisite_failed" -> R.string.blackflow_outcome_ending2_prerequisite_failed
        "ending3_prerequisite_failed" -> R.string.blackflow_outcome_ending3_prerequisite_failed
        "baby_cultivation_unfinished" -> R.string.blackflow_outcome_baby_cultivation_unfinished
        "task_event_failed" -> R.string.blackflow_outcome_task_event_failed
        "perception_port_missing" -> R.string.blackflow_outcome_perception_port_missing
        "map_rebuild_failed" -> R.string.blackflow_outcome_map_rebuild_failed
        "planning_failed" -> R.string.blackflow_outcome_planning_failed
        "transaction_proposal_failed" -> R.string.blackflow_outcome_transaction_proposal_failed
        "move_preview_failed" -> R.string.blackflow_outcome_move_preview_failed
        "move_preview_rejected" -> R.string.blackflow_outcome_move_preview_rejected
        "move_confirmation_failed" -> R.string.blackflow_outcome_move_confirmation_failed
        "post_move_validation_failed" -> R.string.blackflow_outcome_post_move_validation_failed
        "planning_retry_exhausted" -> R.string.blackflow_outcome_planning_retry_exhausted
        "state_machine_dead_end" -> R.string.blackflow_outcome_state_machine_dead_end
        "map_recovery_exhausted" -> R.string.blackflow_outcome_map_recovery_exhausted
        "floor_recognition_failed" -> R.string.blackflow_outcome_floor_recognition_failed
        "movement_inventory_observation_failed" ->
            R.string.blackflow_outcome_movement_inventory_failed

        "movement_selection_failed" -> R.string.blackflow_outcome_movement_selection_failed
        "node_dispatch_failed" -> R.string.blackflow_outcome_node_dispatch_failed
        "node_result_failed" -> R.string.blackflow_outcome_node_result_failed
        "internal_failure" -> R.string.blackflow_outcome_internal_failure
        else -> R.string.blackflow_outcome_unknown
    }

    @StringRes
    fun terminationReason(code: String?): Int = when (code) {
        "investment_finished" -> R.string.blackflow_termination_investment_finished
        "investment_shop_window_closed" ->
            R.string.blackflow_termination_investment_shop_window_closed

        "third_floor_reached" -> R.string.blackflow_termination_floor3_reached
        "cultivation_result_reported" -> R.string.blackflow_termination_cultivation_reported
        "cultivation_target_obtained" -> R.string.blackflow_termination_cultivation_target_obtained
        "cultivation_target_not_obtained" ->
            R.string.blackflow_termination_cultivation_target_not_obtained

        "floor1_shop_has_no_seed" -> R.string.blackflow_termination_floor1_shop_no_seed
        "mandatory_prerequisite_missed" ->
            R.string.blackflow_termination_mandatory_prerequisite_missed

        "strategy_terminal_reached" -> R.string.blackflow_termination_strategy_terminal_reached
        "node_page_could_not_return_to_map" ->
            R.string.blackflow_termination_node_page_recovery_failed

        "ending2_terminal_completed" -> R.string.blackflow_termination_ending2_completed
        "ending3_terminal_completed" -> R.string.blackflow_termination_ending3_completed
        "fifth_floor_reached_without_valid_sandtable_payment" ->
            R.string.blackflow_termination_ending2_prerequisite_missing

        "fifth_floor_reached_without_special_device" ->
            R.string.blackflow_termination_ending3_relic_missing

        "third_floor_has_no_portal" -> R.string.blackflow_termination_no_bosky_passage
        "third_floor_action_points_exhausted" ->
            R.string.blackflow_termination_action_points_exhausted_before_cultivation

        "scrap_shop_never_reached" -> R.string.blackflow_termination_scrap_shop_never_reached
        "map recovery port is unavailable" ->
            R.string.blackflow_termination_recovery_port_unavailable

        "BlackFlow perception and task port is not attached" ->
            R.string.blackflow_termination_perception_port_unavailable

        "map rebuild failed twice" -> R.string.blackflow_termination_map_rebuild_failed_twice
        "preview replanning exceeded the finite candidate limit" ->
            R.string.blackflow_termination_planning_retry_exhausted

        else -> R.string.blackflow_termination_unknown
    }

    /** 策略配置里的规则 id，查不到返回 null */
    @StringRes
    fun rule(code: String?): Int? = when (code) {
        "avoid_empty_scrap_shop" -> R.string.blackflow_rule_avoid_empty_scrap_shop
        "preserve_white_model_bird" -> R.string.blackflow_rule_preserve_white_model_bird
        "trigger_boss_processing_bonus" -> R.string.blackflow_rule_trigger_boss_processing_bonus
        "exit_when_required" -> R.string.blackflow_rule_exit_when_required
        "ending1_avoid_late_combat" -> R.string.blackflow_rule_ending1_avoid_late_combat
        "baby_use_processing_item_for_floor1_shop" ->
            R.string.blackflow_rule_baby_use_processing_item_for_floor1_shop

        "baby_walk_after_floor1_shop" -> R.string.blackflow_rule_baby_walk_after_floor1_shop
        "baby_delay_exit_before_floor3" -> R.string.blackflow_rule_baby_delay_exit_before_floor3
        "baby_exit_before_floor3_when_required" ->
            R.string.blackflow_rule_baby_exit_before_floor3_when_required

        "baby_avoid_combat" -> R.string.blackflow_rule_baby_avoid_combat
        "light_reveals_three" -> R.string.blackflow_rule_light_reveals_three
        "investment_keep_short_walk" -> R.string.blackflow_rule_investment_keep_short_walk
        "investment_use_m11_for_direct_shop" ->
            R.string.blackflow_rule_investment_use_m11_for_direct_shop

        "burn_require_shop_when_flight_guaranteed" ->
            R.string.blackflow_rule_burn_require_shop_when_flight_guaranteed

        "burn_require_flight_on_floor2" -> R.string.blackflow_rule_burn_require_flight_on_floor2
        else -> null
    }

    /** 策略配置里的阶段目标 id，查不到返回 null */
    @StringRes
    fun milestone(code: String?): Int? = when (code) {
        "ending1_floor1_battles" -> R.string.blackflow_milestone_ending1_floor1_battles
        "ending1_floor1_hidden" -> R.string.blackflow_milestone_ending1_floor1_hidden
        "ending1_floor2_expedition" -> R.string.blackflow_milestone_ending1_floor2_expedition
        "ending1_floor2_scrap_shop" -> R.string.blackflow_milestone_ending1_floor2_scrap_shop
        "ending1_floor2_hidden" -> R.string.blackflow_milestone_ending1_floor2_hidden
        "ending1_floor2_employ" -> R.string.blackflow_milestone_ending1_floor2_employ
        "ending1_floor2_light" -> R.string.blackflow_milestone_ending1_floor2_light
        "ending1_floor2_wish" -> R.string.blackflow_milestone_ending1_floor2_wish
        "ending1_late_duel" -> R.string.blackflow_milestone_ending1_late_duel
        "ending1_late_expedition" -> R.string.blackflow_milestone_ending1_late_expedition
        "ending1_late_scrap_shop" -> R.string.blackflow_milestone_ending1_late_scrap_shop
        "ending1_late_employ" -> R.string.blackflow_milestone_ending1_late_employ
        "ending1_late_hidden" -> R.string.blackflow_milestone_ending1_late_hidden
        "ending1_late_incident" -> R.string.blackflow_milestone_ending1_late_incident
        "ending1_late_combat" -> R.string.blackflow_milestone_ending1_late_combat
        "ending1_late_light" -> R.string.blackflow_milestone_ending1_late_light
        "ending1_late_wish" -> R.string.blackflow_milestone_ending1_late_wish
        "investment_shop" -> R.string.blackflow_milestone_investment_shop
        "burn_floor1_shop" -> R.string.blackflow_milestone_burn_floor1_shop
        "burn_floor1_final" -> R.string.blackflow_milestone_burn_floor1_final
        "burn_floor2_final" -> R.string.blackflow_milestone_burn_floor2_final
        "baby_check_seed_shop" -> R.string.blackflow_milestone_baby_check_seed_shop
        "baby_cultivate_scrap_shop" -> R.string.blackflow_milestone_baby_cultivate_scrap_shop
        "baby_cultivate_scrap_shop_transit" ->
            R.string.blackflow_milestone_baby_cultivate_scrap_shop_transit
        "baby_cultivate_scrap_shop_final" ->
            R.string.blackflow_milestone_baby_cultivate_scrap_shop_final
        "baby_explore_hidden" -> R.string.blackflow_milestone_baby_explore_hidden
        "baby_visit_incident" -> R.string.blackflow_milestone_baby_visit_incident
        "baby_floor2_shops" -> R.string.blackflow_milestone_baby_floor2_shops
        "baby_floor3_shops" -> R.string.blackflow_milestone_baby_floor3_shops
        "ending2_sandtable_a" -> R.string.blackflow_milestone_ending2_sandtable_a
        "ending2_sandtable_b" -> R.string.blackflow_milestone_ending2_sandtable_b
        "ending2_fate" -> R.string.blackflow_milestone_ending2_fate
        "ending3_device_option2" -> R.string.blackflow_milestone_ending3_device_option2
        "ending3_relics" -> R.string.blackflow_milestone_ending3_relics
        "ending3_floor5_boss" -> R.string.blackflow_milestone_ending3_floor5_boss
        "ending3_floor6" -> R.string.blackflow_milestone_ending3_floor6
        else -> null
    }
}
