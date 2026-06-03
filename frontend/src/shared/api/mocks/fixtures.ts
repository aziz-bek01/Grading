/**
 * Static mock fixtures for Phase 2 — used by the dev mock layer when
 * VITE_USE_MSW=true. NO salary fields anywhere (positions Phase 2 hard rule).
 */
import type { Locale } from '@/shared/types/common';

export type MockProjectStatus = 'DRAFT' | 'ACTIVE' | 'IN_REVIEW' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';
export type MockDepartmentType = 'BRANCH' | 'DEPARTMENT' | 'DIVISION' | 'UNIT';
export type MockEntityStatus = 'ACTIVE' | 'ARCHIVED' | 'DRAFT';

export interface MockProject {
  id: string;
  /** Kept on the mock row for tenant-scoped filtering only — never echoed on the wire. */
  tenant_id: string;
  code: string;
  name_i18n: Partial<Record<Locale, string>>;
  description?: string;
  status: MockProjectStatus;
  methodology_version_id?: string | null;
  start_date?: string;
  end_date?: string;
  updated_at: string;
}

export interface MockDepartment {
  id: string;
  project_id: string;
  parent_id: string | null;
  code: string;
  name_i18n: Partial<Record<Locale, string>>;
  type: MockDepartmentType;
  status: MockEntityStatus;
  updated_at: string;
}

export interface MockPosition {
  id: string;
  project_id: string;
  department_id: string;
  code: string;
  title_i18n: Partial<Record<Locale, string>>;
  function?: string;
  category?: string;
  job_family?: string;
  job_level?: string;
  status: MockEntityStatus;
  updated_at: string;
}

/**
 * Workflow stage progress shape — mirrors backend MVP 2 Phase 1 contract.
 * Status enum: NOT_STARTED | IN_PROGRESS | COMPLETE | BLOCKED | LOCKED_FUTURE.
 */
export interface MockStage {
  stage: string;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETE' | 'BLOCKED' | 'LOCKED_FUTURE';
  completionPercent: number;
  responsibleUserId?: string | null;
  responsibleUserName?: string | null;
  lastUpdatedAt?: string | null;
  lastUpdatedBy?: string | null;
  sortOrder: number;
}

export interface MockWorkflowProgress {
  id: string;
  projectId: string;
  currentStage: string;
  startedAt: string;
  archivedAt?: string | null;
  stages: MockStage[];
}

const projects: MockProject[] = [
  {
    id: 'proj-acme-2026',
    tenant_id: '11111111-1111-1111-1111-111111111111',
    code: 'ACME-2026',
    name_i18n: {
      'ru-RU': 'Грейдинг ACME 2026',
      'en-US': 'ACME Grading 2026',
      'uz-Cyrl-UZ': 'ACME грейдинг 2026',
      'uz-Latn-UZ': 'ACME greyding 2026',
    },
    description: 'Annual grading cycle for ACME Holdings.',
    status: 'ACTIVE',
    start_date: '2026-01-15',
    end_date: '2026-09-30',
    updated_at: '2026-05-12T11:32:00Z',
  },
  {
    id: 'proj-acme-pilot',
    tenant_id: '11111111-1111-1111-1111-111111111111',
    code: 'ACME-PILOT',
    name_i18n: {
      'ru-RU': 'Пилотный проект ACME',
      'en-US': 'ACME Pilot',
      'uz-Cyrl-UZ': 'ACME пилот',
      'uz-Latn-UZ': 'ACME pilot',
    },
    status: 'APPROVED',
    start_date: '2025-09-01',
    end_date: '2025-12-31',
    updated_at: '2026-01-08T09:10:00Z',
  },
  {
    id: 'proj-beta-univ',
    tenant_id: '22222222-2222-2222-2222-222222222222',
    code: 'BETA-2026',
    name_i18n: {
      'ru-RU': 'Грейдинг Beta University',
      'en-US': 'Beta University Grading',
      'uz-Cyrl-UZ': 'Beta университет грейдинги',
      'uz-Latn-UZ': 'Beta universitet greydingi',
    },
    status: 'DRAFT',
    updated_at: '2026-04-20T14:00:00Z',
  },
];

const departments: MockDepartment[] = [
  {
    id: 'dep-acme-hq',
    project_id: 'proj-acme-2026',
    parent_id: null,
    code: 'HQ',
    name_i18n: { 'ru-RU': 'Головной офис', 'en-US': 'Headquarters', 'uz-Cyrl-UZ': 'Бош офис', 'uz-Latn-UZ': 'Bosh ofis' },
    type: 'BRANCH',
    status: 'ACTIVE',
    updated_at: '2026-02-10T10:00:00Z',
  },
  {
    id: 'dep-acme-fin',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'FIN',
    name_i18n: { 'ru-RU': 'Финансы', 'en-US': 'Finance', 'uz-Cyrl-UZ': 'Молия', 'uz-Latn-UZ': 'Moliya' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
    updated_at: '2026-02-12T10:00:00Z',
  },
  {
    id: 'dep-acme-fin-treasury',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-fin',
    code: 'FIN-TR',
    name_i18n: { 'ru-RU': 'Казначейство', 'en-US': 'Treasury', 'uz-Cyrl-UZ': 'Хазина', 'uz-Latn-UZ': 'Xazina' },
    type: 'UNIT',
    status: 'ACTIVE',
    updated_at: '2026-02-14T10:00:00Z',
  },
  {
    id: 'dep-acme-it',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'IT',
    name_i18n: { 'ru-RU': 'ИТ', 'en-US': 'IT', 'uz-Cyrl-UZ': 'АТ', 'uz-Latn-UZ': 'AT' },
    type: 'DIVISION',
    status: 'ACTIVE',
    updated_at: '2026-03-01T10:00:00Z',
  },
  {
    id: 'dep-acme-it-legacy',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-it',
    code: 'IT-LEG',
    name_i18n: { 'ru-RU': 'Поддержка legacy', 'en-US': 'Legacy Support', 'uz-Cyrl-UZ': 'Эски тизим', 'uz-Latn-UZ': 'Eski tizim' },
    type: 'UNIT',
    status: 'ARCHIVED',
    updated_at: '2026-04-10T10:00:00Z',
  },
];

const positions: MockPosition[] = [
  {
    id: 'pos-cfo',
    project_id: 'proj-acme-2026',
    department_id: 'dep-acme-fin',
    code: 'CFO',
    title_i18n: { 'ru-RU': 'Финансовый директор', 'en-US': 'Chief Financial Officer', 'uz-Cyrl-UZ': 'Молия директори', 'uz-Latn-UZ': 'Moliya direktori' },
    function: 'Finance',
    category: 'C-level',
    job_family: 'Finance',
    job_level: 'L10',
    status: 'ACTIVE',
    updated_at: '2026-04-30T08:00:00Z',
  },
  {
    id: 'pos-treas-head',
    project_id: 'proj-acme-2026',
    department_id: 'dep-acme-fin-treasury',
    code: 'TREAS-HEAD',
    title_i18n: { 'ru-RU': 'Руководитель казначейства', 'en-US': 'Head of Treasury', 'uz-Cyrl-UZ': 'Хазина бошлиғи', 'uz-Latn-UZ': 'Xazina boshligʻi' },
    function: 'Finance',
    category: 'Senior Manager',
    job_family: 'Finance',
    job_level: 'L8',
    status: 'ACTIVE',
    updated_at: '2026-05-02T08:00:00Z',
  },
  {
    id: 'pos-cto',
    project_id: 'proj-acme-2026',
    department_id: 'dep-acme-it',
    code: 'CTO',
    title_i18n: { 'ru-RU': 'Технический директор', 'en-US': 'Chief Technology Officer', 'uz-Cyrl-UZ': 'Технологиялар директори', 'uz-Latn-UZ': 'Texnologiyalar direktori' },
    function: 'Technology',
    category: 'C-level',
    job_family: 'IT',
    job_level: 'L10',
    status: 'ACTIVE',
    updated_at: '2026-05-08T08:00:00Z',
  },
  {
    id: 'pos-swe-senior',
    project_id: 'proj-acme-2026',
    department_id: 'dep-acme-it',
    code: 'SWE-SR',
    title_i18n: { 'ru-RU': 'Старший разработчик', 'en-US': 'Senior Software Engineer', 'uz-Cyrl-UZ': 'Катта дастурчи', 'uz-Latn-UZ': 'Katta dasturchi' },
    function: 'Technology',
    category: 'Individual contributor',
    job_family: 'IT',
    job_level: 'L6',
    status: 'ACTIVE',
    updated_at: '2026-05-10T08:00:00Z',
  },
  {
    id: 'pos-swe-mid',
    project_id: 'proj-acme-2026',
    department_id: 'dep-acme-it',
    code: 'SWE-MID',
    title_i18n: { 'ru-RU': 'Разработчик', 'en-US': 'Software Engineer', 'uz-Cyrl-UZ': 'Дастурчи', 'uz-Latn-UZ': 'Dasturchi' },
    function: 'Technology',
    category: 'Individual contributor',
    job_family: 'IT',
    job_level: 'L5',
    status: 'DRAFT',
    updated_at: '2026-05-15T08:00:00Z',
  },
];

/**
 * Workflow progress fixtures — MVP 2 Phase 1 shape.
 *
 * ACME 2026 reflects the seed state called out in the demo backlog:
 *   SETUP / ORGANIZATION = COMPLETE
 *   POSITIONS            = IN_PROGRESS (80%)
 *   JOB_PROFILES         = IN_PROGRESS (25%, CFO done)
 *   METHODOLOGY          = COMPLETE (LOCKED v1)
 *   EVALUATION           = IN_PROGRESS (50%)
 *   CALIBRATION          = NOT_STARTED
 *   GRADES               = COMPLETE (LOCKED 14-grade)
 *   COMPENSATION         = LOCKED_FUTURE
 *   REPORTS              = LOCKED_FUTURE
 *   ARCHIVE              = NOT_STARTED
 */
const workflowProgress: Record<string, MockWorkflowProgress> = {
  'proj-acme-2026': {
    id: 'wf-acme-2026',
    projectId: 'proj-acme-2026',
    currentStage: 'EVALUATION',
    startedAt: '2026-01-15T08:00:00Z',
    archivedAt: null,
    stages: [
      { stage: 'SETUP', status: 'COMPLETE', completionPercent: 100, sortOrder: 0, responsibleUserName: 'HRLab Analyst', lastUpdatedAt: '2026-02-01T08:00:00Z', lastUpdatedBy: 'HRLab Analyst' },
      { stage: 'ORGANIZATION', status: 'COMPLETE', completionPercent: 100, sortOrder: 1, responsibleUserName: 'HRLab Analyst', lastUpdatedAt: '2026-02-12T08:00:00Z', lastUpdatedBy: 'HRLab Analyst' },
      { stage: 'POSITIONS', status: 'IN_PROGRESS', completionPercent: 80, sortOrder: 2, responsibleUserName: 'HRLab Analyst', lastUpdatedAt: '2026-04-30T08:00:00Z', lastUpdatedBy: 'HRLab Analyst' },
      { stage: 'JOB_PROFILES', status: 'IN_PROGRESS', completionPercent: 25, sortOrder: 3, responsibleUserName: 'HRLab Consultant', lastUpdatedAt: '2026-04-22T10:00:00Z', lastUpdatedBy: 'HRLab Consultant' },
      { stage: 'METHODOLOGY', status: 'COMPLETE', completionPercent: 100, sortOrder: 4, responsibleUserName: 'HRLab Consultant', lastUpdatedAt: '2026-03-25T08:00:00Z', lastUpdatedBy: 'HRLab Super Admin' },
      { stage: 'EVALUATION', status: 'IN_PROGRESS', completionPercent: 50, sortOrder: 5, responsibleUserName: 'HRLab Consultant', lastUpdatedAt: '2026-05-15T08:00:00Z', lastUpdatedBy: 'mock-evaluator-1' },
      { stage: 'CALIBRATION', status: 'NOT_STARTED', completionPercent: 0, sortOrder: 6 },
      { stage: 'GRADES', status: 'COMPLETE', completionPercent: 100, sortOrder: 7, responsibleUserName: 'HRLab Super Admin', lastUpdatedAt: '2026-04-10T08:00:00Z', lastUpdatedBy: 'HRLab Super Admin' },
      { stage: 'COMPENSATION', status: 'LOCKED_FUTURE', completionPercent: 0, sortOrder: 8 },
      { stage: 'REPORTS', status: 'LOCKED_FUTURE', completionPercent: 0, sortOrder: 9 },
      { stage: 'ARCHIVE', status: 'NOT_STARTED', completionPercent: 0, sortOrder: 10 },
    ],
  },
};

// ============================================================
// Phase 3 — Job Profile + Job Analysis fixtures
// ============================================================

export type MockJobProfileStatus = 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'ARCHIVED';

export interface MockJobProfile {
  id: string;
  position_id: string;
  project_id: string;
  status: MockJobProfileStatus;
  revision_number: number;
  previous_revision_id?: string | null;
  purpose_i18n: Partial<Record<Locale, string>>;
  main_duties_i18n: Partial<Record<Locale, string>>;
  responsibility_area_i18n: Partial<Record<Locale, string>>;
  authority_i18n: Partial<Record<Locale, string>>;
  kpi_expected_results_i18n: Partial<Record<Locale, string>>;
  education_requirements_i18n: Partial<Record<Locale, string>>;
  experience_requirements_i18n: Partial<Record<Locale, string>>;
  knowledge_skills_i18n: Partial<Record<Locale, string>>;
  internal_interactions_i18n: Partial<Record<Locale, string>>;
  external_interactions_i18n: Partial<Record<Locale, string>>;
  working_conditions_i18n: Partial<Record<Locale, string>>;
  documents_regulations_i18n: Partial<Record<Locale, string>>;
  actualization_date?: string;
  created_at: string;
  updated_at: string;
  submitted_at?: string | null;
  submitted_by?: string | null;
  approved_at?: string | null;
  approved_by?: string | null;
  locked_at?: string | null;
  archived_at?: string | null;
  archived_by?: string | null;
  archive_reason?: string | null;
}

export type MockQuestionType =
  | 'TEXT'
  | 'LONG_TEXT'
  | 'SINGLE_CHOICE'
  | 'MULTI_CHOICE'
  | 'RATING_SCALE'
  | 'NUMBER';

export interface MockQuestion {
  id: string;
  code: string;
  question_type: MockQuestionType;
  prompt: Partial<Record<Locale, string>>;
  help_text?: Partial<Record<Locale, string>>;
  required: boolean;
  sort_order: number;
  choices?: { code: string; label: Partial<Record<Locale, string>> }[];
  scale_min?: number;
  scale_max?: number;
}

export interface MockQuestionnaireTemplate {
  code: string;
  name: Partial<Record<Locale, string>>;
  description?: Partial<Record<Locale, string>>;
  questions: MockQuestion[];
}

export interface MockQuestionnaire {
  id: string;
  position_id: string;
  project_id: string;
  template_code: string;
  name: Partial<Record<Locale, string>>;
  status: 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED';
  questions: MockQuestion[];
  answers: { question_id: string; value: unknown }[];
  created_at: string;
  updated_at: string;
}

/**
 * PO-1: explicit 4-locale literal. Every fixture multilingual field must
 * use this helper so that no `Partial<Record<Locale, string>>` literal in
 * fixtures.ts can sneak through with duplicated Russian/English text in
 * the Uzbek slots. See `docs/mvp1/reviews/po-comprehensive-audit-phase0-4.md` §5.
 */
function I18N(
  ru: string,
  uzCyrl: string,
  uzLatn: string,
  en: string,
): Partial<Record<Locale, string>> {
  return {
    'ru-RU': ru,
    'uz-Cyrl-UZ': uzCyrl,
    'uz-Latn-UZ': uzLatn,
    'en-US': en,
  };
}

const jobProfiles: MockJobProfile[] = [
  {
    id: 'jp-cfo-v1',
    position_id: 'pos-cfo',
    project_id: 'proj-acme-2026',
    status: 'APPROVED',
    revision_number: 1,
    previous_revision_id: null,
    purpose_i18n: I18N(
      'Обеспечение финансовой устойчивости компании.',
      'Компаниянинг молиявий барқарорлигини таъминлаш.',
      "Kompaniyaning moliyaviy barqarorligini ta'minlash.",
      "Ensure the company's financial sustainability.",
    ),
    main_duties_i18n: I18N(
      'Управление бюджетом, отчётность, контроль рисков.',
      'Бюджетни бошқариш, ҳисоботлар, хатарларни назорат қилиш.',
      "Byudjetni boshqarish, hisobotlar, xatarlarni nazorat qilish.",
      'Budget management, reporting, risk control.',
    ),
    responsibility_area_i18n: I18N(
      'Все финансовые операции и отчётность.',
      'Барча молиявий операциялар ва ҳисоботлар.',
      "Barcha moliyaviy operatsiyalar va hisobotlar.",
      'All financial operations and reporting.',
    ),
    authority_i18n: I18N(
      'Утверждение бюджетов до 10 млн.',
      '10 миллионгача бюджетларни тасдиқлаш.',
      "10 milliongacha byudjetlarni tasdiqlash.",
      'Approve budgets up to 10 million.',
    ),
    kpi_expected_results_i18n: I18N(
      'Чистая прибыль, ROE, cost-income ratio.',
      'Соф фойда, ROE, харажат-даромад нисбати.',
      "Sof foyda, ROE, xarajat-daromad nisbati.",
      'Net profit, ROE, cost-income ratio.',
    ),
    education_requirements_i18n: I18N(
      'Высшее экономическое/финансовое; CFA/MBA приветствуется.',
      'Олий иқтисодий/молиявий; CFA/MBA маъқул.',
      "Oliy iqtisodiy/moliyaviy; CFA/MBA ma'qul.",
      'Higher economic/financial education; CFA/MBA preferred.',
    ),
    experience_requirements_i18n: I18N(
      'Не менее 7 лет в финансах, из них 3 года на руководящей позиции.',
      'Молияда камида 7 йил, шу жумладан 3 йил раҳбарлик лавозимида.',
      "Moliyada kamida 7 yil, shu jumladan 3 yil rahbarlik lavozimida.",
      'At least 7 years in finance, including 3 years in a leadership role.',
    ),
    knowledge_skills_i18n: I18N(
      'МСФО, бюджетирование, риск-менеджмент, корпоративные финансы.',
      'МСФО, бюджетлаш, хатарларни бошқариш, корпоратив молия.',
      "MSFO, byudjetlash, xatarlarni boshqarish, korporativ moliya.",
      'IFRS, budgeting, risk management, corporate finance.',
    ),
    internal_interactions_i18n: I18N(
      'CEO, совет директоров, руководители подразделений.',
      'CEO, директорлар кенгаши, бўлинма раҳбарлари.',
      "CEO, direktorlar kengashi, bo'linma rahbarlari.",
      'CEO, board of directors, department heads.',
    ),
    external_interactions_i18n: I18N(
      'Аудиторы, банки, налоговые органы, инвесторы.',
      'Аудиторлар, банклар, солиқ органлари, инвесторлар.',
      "Auditorlar, banklar, soliq organlari, investorlar.",
      'Auditors, banks, tax authorities, investors.',
    ),
    working_conditions_i18n: I18N(
      'Офис, ненормированный рабочий день, командировки.',
      'Офис, белгиланмаган иш куни, командировкалар.',
      "Ofis, belgilanmagan ish kuni, komandirovkalar.",
      'Office, non-standard hours, business trips.',
    ),
    documents_regulations_i18n: I18N(
      'ФЗ о бухучёте, МСФО, корпоративные политики.',
      "Бухгалтерия ҳисоби тўғрисидаги қонун, МСФО, корпоратив сиёсат.",
      "Buxgalteriya hisobi to'g'risidagi qonun, MSFO, korporativ siyosat.",
      'Accounting law, IFRS, corporate policies.',
    ),
    actualization_date: '2026-04-15',
    created_at: '2026-02-01T10:00:00Z',
    updated_at: '2026-04-30T12:00:00Z',
    approved_at: '2026-04-30T12:00:00Z',
    approved_by: 'HR Director',
  },
  {
    id: 'jp-swe-senior-v1',
    position_id: 'pos-swe-senior',
    project_id: 'proj-acme-2026',
    status: 'DRAFT',
    revision_number: 1,
    previous_revision_id: null,
    purpose_i18n: { 'ru-RU': 'Разработка высоконагруженных backend сервисов.' },
    main_duties_i18n: { 'ru-RU': 'Проектирование, разработка, code review.' },
    responsibility_area_i18n: { 'ru-RU': 'Микросервисы платёжной платформы.' },
    authority_i18n: {},
    kpi_expected_results_i18n: { 'ru-RU': 'SLA, throughput, code coverage.' },
    education_requirements_i18n: { 'ru-RU': 'Высшее техническое.' },
    experience_requirements_i18n: { 'ru-RU': '5+ лет.' },
    knowledge_skills_i18n: { 'ru-RU': 'Java, Kotlin, Spring Boot, PostgreSQL.' },
    internal_interactions_i18n: {},
    external_interactions_i18n: {},
    working_conditions_i18n: {},
    documents_regulations_i18n: {},
    actualization_date: undefined,
    created_at: '2026-05-10T08:00:00Z',
    updated_at: '2026-05-12T08:00:00Z',
    approved_at: null,
    approved_by: null,
  },
];

// PO-4: real HR consultant-authored prompts replacing the prior "Question N" placeholders.
const standardTemplate: MockQuestionnaireTemplate = {
  code: 'STANDARD_V1',
  name: I18N(
    'Стандартный анализ должности',
    'Стандарт лавозим таҳлили',
    'Standart lavozim tahlili',
    'Standard Job Analysis',
  ),
  description: I18N(
    '8 базовых вопросов о должности.',
    'Лавозим бўйича 8 та асосий савол.',
    "Lavozim bo'yicha 8 ta asosiy savol.",
    '8 baseline questions about the position.',
  ),
  questions: [
    {
      id: 'q-std-1',
      code: 'PURPOSE',
      question_type: 'LONG_TEXT',
      prompt: I18N(
        'Опишите основную цель должности в одном предложении.',
        'Лавозимнинг асосий мақсадини бир жумлада тавсифланг.',
        'Lavozimning asosiy maqsadini bir jumlada tavsiflang.',
        'Describe the primary purpose of the position in one sentence.',
      ),
      required: true,
      sort_order: 1,
    },
    {
      id: 'q-std-2',
      code: 'MAIN_DUTIES',
      question_type: 'LONG_TEXT',
      prompt: I18N(
        'Перечислите 5–7 основных обязанностей.',
        '5–7 та асосий мажбуриятни санаб ўтинг.',
        "5–7 ta asosiy majburiyatni sanab o'ting.",
        'List 5–7 main duties.',
      ),
      required: true,
      sort_order: 2,
    },
    {
      id: 'q-std-3',
      code: 'DIRECT_REPORTS',
      question_type: 'SINGLE_CHOICE',
      prompt: I18N(
        'Сколько прямых подчинённых?',
        'Қанча тўғридан-тўғри бўйсунувчилар?',
        "Qancha to'g'ridan-to'g'ri bo'ysunuvchilar?",
        'How many direct reports?',
      ),
      required: true,
      sort_order: 3,
      choices: [
        { code: 'NONE', label: I18N('Нет', "Йўқ", "Yo'q", 'No direct reports') },
        { code: '1_5', label: I18N('1–5', '1–5', '1–5', '1–5') },
        { code: '6_20', label: I18N('6–20', '6–20', '6–20', '6–20') },
        { code: '21_50', label: I18N('21–50', '21–50', '21–50', '21–50') },
        { code: '50_PLUS', label: I18N('50+', '50+', '50+', '50+') },
      ],
    },
    {
      id: 'q-std-4',
      code: 'DECISION_LEVEL',
      question_type: 'SINGLE_CHOICE',
      prompt: I18N(
        'Уровень принимаемых решений?',
        'Қабул қилинадиган қарорлар даражаси?',
        "Qabul qilinadigan qarorlar darajasi?",
        'Decision-making level?',
      ),
      required: true,
      sort_order: 4,
      choices: [
        { code: 'NONE', label: I18N('Нет', "Йўқ", "Yo'q", 'None') },
        { code: 'OPERATIONAL', label: I18N('Операционный', 'Оператив', 'Operativ', 'Operational decisions') },
        { code: 'TACTICAL', label: I18N('Тактический', 'Тактик', 'Taktik', 'Tactical') },
        { code: 'STRATEGIC', label: I18N('Стратегический', 'Стратегик', 'Strategik', 'Strategic') },
      ],
    },
    {
      id: 'q-std-5',
      code: 'ANALYSIS_COMPLEXITY',
      question_type: 'RATING_SCALE',
      prompt: I18N(
        'Сложность анализа информации (1–5).',
        'Маълумотларни таҳлил қилиш мураккаблиги (1–5).',
        "Ma'lumotlarni tahlil qilish murakkabligi (1–5).",
        'Complexity of information analysis (1–5).',
      ),
      required: true,
      sort_order: 5,
      scale_min: 1,
      scale_max: 5,
    },
    {
      id: 'q-std-6',
      code: 'REPORTS_TO',
      question_type: 'LONG_TEXT',
      prompt: I18N(
        'Кому отчитывается должность?',
        'Лавозим кимга ҳисобот беради?',
        'Lavozim kimga hisobot beradi?',
        'Who does the position report to?',
      ),
      required: true,
      sort_order: 6,
    },
    {
      id: 'q-std-7',
      code: 'EXTERNAL_INTERACTIONS',
      question_type: 'LONG_TEXT',
      prompt: I18N(
        'Опишите ключевые внешние взаимодействия.',
        'Асосий ташқи ўзаро таъсирларни тавсифланг.',
        "Asosiy tashqi o'zaro ta'sirlarni tavsiflang.",
        'Describe key external interactions.',
      ),
      required: true,
      sort_order: 7,
    },
    {
      id: 'q-std-8',
      code: 'EDUCATION_EXPERIENCE',
      question_type: 'LONG_TEXT',
      prompt: I18N(
        'Требования к образованию и опыту.',
        'Таълим ва тажрибага талаблар.',
        "Ta'lim va tajribaga talablar.",
        'Education and experience requirements.',
      ),
      required: true,
      sort_order: 8,
    },
  ],
};

const executiveTemplate: MockQuestionnaireTemplate = {
  code: 'EXECUTIVE_V1',
  name: I18N(
    'Анализ руководящей должности',
    'Раҳбарлик лавозими таҳлили',
    'Rahbarlik lavozimi tahlili',
    'Executive Job Analysis',
  ),
  description: I18N(
    '11 вопросов для C-level / Senior Manager.',
    'C-даражали лавозимлар учун 11 савол.',
    "C-darajali lavozimlar uchun 11 savol.",
    '11 questions for C-level / Senior Manager roles.',
  ),
  questions: [
    // Inherit STANDARD_V1's eight prompts at sort_order 1..8, then add three executive-only prompts.
    ...standardTemplate.questions.map((q) => ({
      ...q,
      id: q.id.replace('q-std-', 'q-exec-'),
    })),
    {
      id: 'q-exec-9',
      code: 'FINANCIAL_IMPACT',
      question_type: 'RATING_SCALE',
      prompt: I18N(
        'Финансовое влияние решений (1–5).',
        'Қарорларнинг молиявий таъсири (1–5).',
        "Qarorlarning moliyaviy ta'siri (1–5).",
        'Financial impact of decisions (1–5).',
      ),
      required: true,
      sort_order: 9,
      scale_min: 1,
      scale_max: 5,
    },
    {
      id: 'q-exec-10',
      code: 'GEOGRAPHIC_SCOPE',
      question_type: 'SINGLE_CHOICE',
      prompt: I18N(
        'Географическая зона влияния?',
        "Таъсир кўламининг географияси?",
        "Ta'sir ko'lamining geografiyasi?",
        'Geographic scope of influence?',
      ),
      required: true,
      sort_order: 10,
      choices: [
        { code: 'LOCAL', label: I18N('Локальная', 'Маҳаллий', 'Mahalliy', 'Local') },
        { code: 'REGIONAL', label: I18N('Региональная', 'Минтақавий', 'Mintaqaviy', 'Regional') },
        { code: 'NATIONAL', label: I18N('Национальная', 'Миллий', 'Milliy', 'National') },
        { code: 'INTERNATIONAL', label: I18N('Международная', 'Халқаро', 'Xalqaro', 'International') },
      ],
    },
    {
      id: 'q-exec-11',
      code: 'UNIQUE_COMPETENCE',
      question_type: 'LONG_TEXT',
      prompt: I18N(
        'Уникальная компетенция, требуемая для должности.',
        "Лавозим учун зарур бўлган ноёб компетенция.",
        "Lavozim uchun zarur bo'lgan noyob kompetensiya.",
        'Unique competence required for the position.',
      ),
      required: true,
      sort_order: 11,
    },
  ],
};

const questionnaireTemplates: MockQuestionnaireTemplate[] = [standardTemplate, executiveTemplate];

const questionnaires: MockQuestionnaire[] = [
  {
    id: 'ques-cfo-1',
    position_id: 'pos-cfo',
    project_id: 'proj-acme-2026',
    template_code: 'EXECUTIVE_V1',
    name: executiveTemplate.name,
    status: 'IN_PROGRESS',
    questions: executiveTemplate.questions,
    answers: [
      { question_id: 'q-exec-1', value: 'Обеспечение финансовой устойчивости и стратегии компании.' },
      { question_id: 'q-exec-2', value: 'Бюджет, отчётность, MSFO, риск-менеджмент, инвестиции, аудит, налоги.' },
      { question_id: 'q-exec-3', value: '6_20' },
      { question_id: 'q-exec-4', value: 'STRATEGIC' },
      { question_id: 'q-exec-5', value: 4 },
      { question_id: 'q-exec-9', value: 5 },
      { question_id: 'q-exec-10', value: 'NATIONAL' },
    ],
    created_at: '2026-04-10T10:00:00Z',
    updated_at: '2026-05-01T10:00:00Z',
  },
];

// ============================================================
// Phase 4 — Methodology fixtures
// ============================================================

export type MockMethodologyType = 'CLASSIC_8_FACTOR' | 'EXTENDED_11_CRITERIA' | 'CUSTOM';
export type MockScoringMode = 'DIRECT_POINTS' | 'WEIGHTED_POINTS' | 'WEIGHTED_SCALE';
export type MockMethodologyVersionStatus = 'DRAFT' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';

export interface MockFactorLevel {
  id: string;
  factor_id: string;
  code: string;
  level_order: number;
  points: number;
  scale_value: number;
  label_i18n: Partial<Record<Locale, string>>;
  description_i18n?: Partial<Record<Locale, string>>;
}

export interface MockFactor {
  id: string;
  methodology_version_id: string;
  code: string;
  name_i18n: Partial<Record<Locale, string>>;
  description_i18n?: Partial<Record<Locale, string>>;
  weight: number;
  max_points: number;
  sort_order: number;
  required: boolean;
  levels: MockFactorLevel[];
}

export interface MockMethodologyVersion {
  id: string;
  methodology_id: string;
  project_id: string;
  version_number: number;
  status: MockMethodologyVersionStatus;
  scoring_mode: MockScoringMode;
  target_total_points: number;
  factors: MockFactor[];
  approved_at?: string | null;
  approved_by?: string | null;
  /** D-407: human-readable actor name resolved server-side. */
  approved_by_name?: string | null;
  locked_at?: string | null;
  locked_by?: string | null;
  /** D-407: human-readable actor name resolved server-side. */
  locked_by_name?: string | null;
  archived_at?: string | null;
  archive_reason?: string | null;
  parent_version_id?: string | null;
  created_at: string;
  updated_at: string;
}

export interface MockMethodology {
  id: string;
  project_id: string;
  code: string;
  name_i18n: Partial<Record<Locale, string>>;
  description_i18n?: Partial<Record<Locale, string>>;
  methodology_type: MockMethodologyType;
  status: 'ACTIVE' | 'ARCHIVED';
  latest_version_id?: string | null;
  active_version_id?: string | null;
  active_version_number?: number | null;
  active_version_status?: MockMethodologyVersionStatus | null;
  created_at: string;
  updated_at: string;
  archived_at?: string | null;
  archive_reason?: string | null;
}

/**
 * PO-1: Real 4-locale translations for HR grading factor names. Replaces
 * the old `LOCALE_PREFIX(ru, en)` helper which fabricated Uzbek by
 * duplicating Russian/English strings into the Uzbek slots.
 *
 * Tuple order: [ru, uz-Cyrl, uz-Latn, en].
 */
const FACTOR_NAME: Record<string, [string, string, string, string]> = {
  // Classic 8-factor
  KNOWLEDGE: ['Знания', 'Билим', 'Bilim', 'Knowledge'],
  EXPERIENCE: ['Опыт', 'Тажриба', 'Tajriba', 'Experience'],
  COMPLEXITY: ['Сложность', 'Мураккаблик', 'Murakkablik', 'Complexity'],
  RESPONSIBILITY: ['Ответственность', 'Жавобгарлик', 'Javobgarlik', 'Responsibility'],
  AUTONOMY: ['Автономия', 'Мустақиллик', 'Mustaqillik', 'Autonomy'],
  INFLUENCE: ['Влияние', 'Таъсир', "Ta'sir", 'Influence'],
  COMMUNICATION: ['Коммуникация', 'Мулоқот', 'Muloqot', 'Communication'],
  WORKING_CONDITIONS: ['Условия труда', 'Меҳнат шароити', 'Mehnat sharoiti', 'Working conditions'],
  // Extended 11-criteria (uses some of the codes above + new ones)
  TECHNICAL_SKILLS: ['Технические навыки', 'Техник кўникмалар', "Texnik ko'nikmalar", 'Technical skills'],
  TEAM_LEADERSHIP: ['Лидерство команды', 'Жамоа етакчилиги', 'Jamoa yetakchiligi', 'Team leadership'],
  INNOVATION: ['Инновации', 'Инновациялар', 'Innovatsiyalar', 'Innovation'],
};

/** Standard 5-level scale labels. */
const LEVEL_LABEL: [string, string, string, string][] = [
  ['Базовый', 'Бошланғич', "Boshlang'ich", 'Basic'],
  ['Начальный', 'Бошланувчи', 'Boshlanuvchi', 'Beginning'],
  ['Средний', 'Ўрта', "O'rta", 'Intermediate'],
  ['Продвинутый', 'Илғор', "Ilg'or", 'Advanced'],
  ['Экспертный', 'Эксперт', 'Ekspert', 'Expert'],
];

function buildLevels(
  factorId: string,
  count: number,
  pointsScale: number[],
): MockFactorLevel[] {
  return Array.from({ length: count }, (_, i) => {
    const tuple = LEVEL_LABEL[i] ?? LEVEL_LABEL[LEVEL_LABEL.length - 1];
    return {
      id: `${factorId}-lvl-${i + 1}`,
      factor_id: factorId,
      code: String.fromCharCode(65 + i),
      level_order: i,
      points: pointsScale[i] ?? (i + 1) * 5,
      scale_value: (i + 1) / count,
      label_i18n: I18N(tuple[0], tuple[1], tuple[2], tuple[3]),
    };
  });
}

// Template — CLASSIC_8_FACTOR (8 factors × 5 levels)
function buildClassic8Factors(versionId: string): MockFactor[] {
  const meta: { code: string; weight: number }[] = [
    { code: 'KNOWLEDGE', weight: 15 },
    { code: 'EXPERIENCE', weight: 12 },
    { code: 'COMPLEXITY', weight: 14 },
    { code: 'RESPONSIBILITY', weight: 15 },
    { code: 'AUTONOMY', weight: 10 },
    { code: 'INFLUENCE', weight: 12 },
    { code: 'COMMUNICATION', weight: 10 },
    { code: 'WORKING_CONDITIONS', weight: 12 },
  ];
  return meta.map((m, i) => {
    const factorId = `${versionId}-f-${m.code}`;
    const t = FACTOR_NAME[m.code];
    return {
      id: factorId,
      methodology_version_id: versionId,
      code: m.code,
      name_i18n: I18N(t[0], t[1], t[2], t[3]),
      weight: m.weight,
      max_points: 100,
      sort_order: i,
      required: true,
      levels: buildLevels(factorId, 5, [10, 25, 50, 75, 100]),
    };
  });
}

// Template — EXTENDED_11_CRITERIA (11 factors × 5 levels)
function buildExtended11Factors(versionId: string): MockFactor[] {
  const meta: { code: string; weight: number }[] = [
    { code: 'KNOWLEDGE', weight: 10 },
    { code: 'EXPERIENCE', weight: 9 },
    { code: 'TECHNICAL_SKILLS', weight: 9 },
    { code: 'COMPLEXITY', weight: 10 },
    { code: 'AUTONOMY', weight: 8 },
    { code: 'RESPONSIBILITY', weight: 10 },
    { code: 'INFLUENCE', weight: 10 },
    { code: 'TEAM_LEADERSHIP', weight: 9 },
    { code: 'COMMUNICATION', weight: 8 },
    { code: 'WORKING_CONDITIONS', weight: 8 },
    { code: 'INNOVATION', weight: 9 },
  ];
  return meta.map((m, i) => {
    const factorId = `${versionId}-f-${m.code}`;
    const t = FACTOR_NAME[m.code];
    return {
      id: factorId,
      methodology_version_id: versionId,
      code: m.code,
      name_i18n: I18N(t[0], t[1], t[2], t[3]),
      weight: m.weight,
      max_points: 100,
      sort_order: i,
      required: true,
      levels: buildLevels(factorId, 5, [10, 25, 50, 75, 100]),
    };
  });
}

const methodologyTemplates: { code: 'CLASSIC_8_FACTOR' | 'EXTENDED_11_CRITERIA'; factors: (versionId: string) => MockFactor[] }[] = [
  { code: 'CLASSIC_8_FACTOR', factors: buildClassic8Factors },
  { code: 'EXTENDED_11_CRITERIA', factors: buildExtended11Factors },
];

// CFO Finance methodology — v1 APPROVED, v2 DRAFT
const cfoV1Id = 'mv-cfo-v1';
const cfoV2Id = 'mv-cfo-v2';
const cfoV1Factors = buildClassic8Factors(cfoV1Id);
const cfoV2Factors = buildClassic8Factors(cfoV2Id);

const methodologies: MockMethodology[] = [
  {
    id: 'meth-cfo-finance',
    project_id: 'proj-acme-2026',
    code: 'CFO-FIN',
    name_i18n: I18N(
      'CFO Финансы — методология',
      'CFO Молия — методология',
      'CFO Moliya — metodologiya',
      'CFO Finance — methodology',
    ),
    description_i18n: I18N(
      'Методология грейдинга для финансовых должностей.',
      'Молиявий лавозимлар учун грейдинг методологияси.',
      'Moliyaviy lavozimlar uchun greyding metodologiyasi.',
      'Grading methodology for finance positions.',
    ),
    methodology_type: 'CLASSIC_8_FACTOR',
    status: 'ACTIVE',
    latest_version_id: cfoV2Id,
    active_version_id: cfoV1Id,
    active_version_number: 1,
    active_version_status: 'APPROVED',
    created_at: '2026-02-01T10:00:00Z',
    updated_at: '2026-05-12T10:00:00Z',
  },
];

const methodologyVersions: MockMethodologyVersion[] = [
  {
    id: cfoV1Id,
    methodology_id: 'meth-cfo-finance',
    project_id: 'proj-acme-2026',
    version_number: 1,
    status: 'APPROVED',
    scoring_mode: 'WEIGHTED_POINTS',
    target_total_points: 1000,
    factors: cfoV1Factors,
    approved_at: '2026-04-12T10:00:00Z',
    approved_by: '7e9c1234-5678-90ab-cdef-1234567890ab',
    approved_by_name: 'Dilshod Karimov',
    locked_at: '2026-04-12T10:00:00Z',
    locked_by: '7e9c1234-5678-90ab-cdef-1234567890ab',
    locked_by_name: 'Dilshod Karimov',
    parent_version_id: null,
    created_at: '2026-02-01T10:00:00Z',
    updated_at: '2026-04-12T10:00:00Z',
  },
  {
    id: cfoV2Id,
    methodology_id: 'meth-cfo-finance',
    project_id: 'proj-acme-2026',
    version_number: 2,
    status: 'DRAFT',
    scoring_mode: 'WEIGHTED_POINTS',
    target_total_points: 1000,
    factors: cfoV2Factors,
    parent_version_id: cfoV1Id,
    created_at: '2026-05-01T10:00:00Z',
    updated_at: '2026-05-12T10:00:00Z',
  },
];

// ============================================================
// Phase 5 — Evaluation fixtures
// ============================================================

export type MockEvaluationStatus =
  | 'DRAFT'
  | 'INCOMPLETE'
  | 'COMPLETE'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'LOCKED'
  | 'ARCHIVED';

export interface MockEvaluation {
  id: string;
  project_id: string;
  position_id: string;
  methodology_version_id: string;
  evaluator_user_id?: string | null;
  status: MockEvaluationStatus;
  raw_total_score?: number | null;
  displayed_total_score?: number | null;
  grade_band_id?: string | null;
  assigned_grade_number?: number | null;
  submitted_at?: string | null;
  submitted_by?: string | null;
  approved_at?: string | null;
  approved_by?: string | null;
  locked_at?: string | null;
  locked_by?: string | null;
  archived_at?: string | null;
  archived_by?: string | null;
}

export interface MockEvaluationScore {
  id: string;
  evaluation_id: string;
  factor_id: string;
  factor_level_id: string;
  raw_factor_score: number;
  comment_text?: string | null;
  manually_adjusted: boolean;
  original_factor_score?: number | null;
  adjusted_by_user_id?: string | null;
  adjusted_at?: string | null;
  adjustment_reason?: string | null;
}

export interface MockCalibrationEvent {
  id: string;
  evaluation_id: string;
  factor_id: string;
  original_score: number;
  adjusted_score: number;
  delta: number;
  reason: string;
  decided_by?: string | null;
  decided_at: string;
}

const cfoEvaluationId = 'eval-cfo-1';
// Pre-compute scored levels: pick the 4th level (index 3) for every CFO factor.
const cfoEvaluationScores: MockEvaluationScore[] = cfoV1Factors.map((f, idx) => {
  const lvl = f.levels[3]; // L=4 ("Advanced") with 75 points
  // WEIGHTED_POINTS: weight * (75/100) = weight * 0.75
  const raw = Number((f.weight * 0.75).toFixed(4));
  return {
    id: `evscore-cfo-${f.code}`,
    evaluation_id: cfoEvaluationId,
    factor_id: f.id,
    factor_level_id: lvl.id,
    raw_factor_score: raw,
    comment_text: idx === 0 ? 'Strong domain knowledge.' : null,
    manually_adjusted: false,
  };
});
const cfoRawTotal = Number(
  cfoEvaluationScores
    .reduce((acc, s) => acc + s.raw_factor_score, 0)
    .toFixed(4),
);

const sweEvaluationId = 'eval-swe-1';
// Senior SWE — pick varied levels (idx 2, 3, 2, 3, 2, 3, 2, 3) for demo.
const swePicks = [2, 3, 2, 3, 2, 3, 2, 3];
const sweEvaluationScores: MockEvaluationScore[] = cfoV1Factors.map((f, i) => {
  const lvlIdx = swePicks[i] ?? 2;
  const lvl = f.levels[lvlIdx];
  const ratio = lvl.points / 100;
  const raw = Number((f.weight * ratio).toFixed(4));
  return {
    id: `evscore-swe-${f.code}`,
    evaluation_id: sweEvaluationId,
    factor_id: f.id,
    factor_level_id: lvl.id,
    raw_factor_score: raw,
    comment_text: null,
    manually_adjusted: false,
  };
});
const sweRawTotal = Number(
  sweEvaluationScores
    .reduce((acc, s) => acc + s.raw_factor_score, 0)
    .toFixed(4),
);

/**
 * ============================================================
 * Phase 6 — Grade Structure fixtures
 * ============================================================
 *
 * ACME runs a 14-grade structure that's APPROVED + LOCKED. We choose a band
 * distribution that lets the existing CFO eval (`displayed_total ≈ 75`) and
 * Senior SWE eval map to meaningful (non-G1) grades for the demo:
 *
 *   G1 [0,    50)        G8  [350, 450)
 *   G2 [50,  100)        G9  [450, 550)
 *   G3 [100, 150)        G10 [550, 650)
 *   G4 [150, 200)        G11 [650, 750)
 *   G5 [200, 250)        G12 [750, 850)
 *   G6 [250, 300)        G13 [850, 950)
 *   G7 [300, 350)        G14 [950, 1000]
 *
 * Using inclusive [min,max] with a 0.0001 epsilon between bands to mirror
 * the backend convention (numeric 18,4). The CFO eval total of 75 lands in
 * G2; Senior SWE total ~62 lands in G2 too — fine for the demo.
 */
export type MockGradeStructureStatus = 'DRAFT' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';
export type MockGradeStructureType = 'GRADE_14' | 'GRADE_16' | 'CUSTOM';
export type MockGradeGapPolicy = 'STRICT_NO_GAPS' | 'ALLOW_GAPS_WARN';

export interface MockGradeBand {
  id: string;
  grade_id: string;
  min_score: number;
  max_score: number;
}

export interface MockGrade {
  id: string;
  grade_structure_id: string;
  grade_number: number;
  name_i18n: Partial<Record<Locale, string>>;
  description_i18n?: Partial<Record<Locale, string>>;
  sort_order: number;
  band?: MockGradeBand | null;
}

export interface MockGradeStructure {
  id: string;
  project_id?: string | null;
  code: string;
  name_i18n: Partial<Record<Locale, string>>;
  description_i18n?: Partial<Record<Locale, string>>;
  structure_type: MockGradeStructureType;
  status: MockGradeStructureStatus;
  gap_policy: MockGradeGapPolicy;
  version_number: number;
  /** Backend response field name — `previous_version_id` (renamed from `parent_structure_id`). */
  previous_version_id?: string | null;
  /** @deprecated kept for MSW back-compat; mirror `previous_version_id`. */
  parent_structure_id?: string | null;
  approved_at?: string | null;
  approved_by?: string | null;
  approved_by_name?: string | null;
  locked_at?: string | null;
  locked_by?: string | null;
  locked_by_name?: string | null;
  archived_at?: string | null;
  archive_reason?: string | null;
  created_at: string;
  updated_at: string;
  grades: MockGrade[];
}

function gradeName14(n: number): Partial<Record<Locale, string>> {
  return I18N(`Грейд ${n}`, `Грейд ${n}`, `Greyd ${n}`, `Grade ${n}`);
}

function gradeName16(n: number): Partial<Record<Locale, string>> {
  return I18N(`Грейд ${n}`, `Грейд ${n}`, `Greyd ${n}`, `Grade ${n}`);
}

// ACME 14-grade structure
const acmeStructureId = 'gs-acme-14';
// Tuned band ranges (see comment above): widths vary to model a real org.
const acmeBandRanges: [number, number][] = [
  [0, 50],
  [50.0001, 100],
  [100.0001, 150],
  [150.0001, 200],
  [200.0001, 250],
  [250.0001, 300],
  [300.0001, 350],
  [350.0001, 450],
  [450.0001, 550],
  [550.0001, 650],
  [650.0001, 750],
  [750.0001, 850],
  [850.0001, 950],
  [950.0001, 1000],
];

const acmeGrades: MockGrade[] = acmeBandRanges.map((range, i) => {
  const n = i + 1;
  const gradeId = `${acmeStructureId}-g-${n}`;
  return {
    id: gradeId,
    grade_structure_id: acmeStructureId,
    grade_number: n,
    name_i18n: gradeName14(n),
    sort_order: i,
    band: {
      id: `${gradeId}-band`,
      grade_id: gradeId,
      min_score: range[0],
      max_score: range[1],
    },
  };
});

// Beta 16-grade structure (APPROVED, not yet locked) — even-width 0..1000.
const betaStructureId = 'gs-beta-16';
const betaGrades: MockGrade[] = Array.from({ length: 16 }, (_, i) => {
  const n = i + 1;
  const gradeId = `${betaStructureId}-g-${n}`;
  const step = 1000 / 16; // 62.5
  const min = i === 0 ? 0 : Number((i * step + 0.0001).toFixed(4));
  const max = i === 15 ? 1000 : Number(((i + 1) * step).toFixed(4));
  return {
    id: gradeId,
    grade_structure_id: betaStructureId,
    grade_number: n,
    name_i18n: gradeName16(n),
    sort_order: i,
    band: {
      id: `${gradeId}-band`,
      grade_id: gradeId,
      min_score: min,
      max_score: max,
    },
  };
});

const gradeStructures: MockGradeStructure[] = [
  {
    id: acmeStructureId,
    project_id: 'proj-acme-2026',
    code: 'ACME-14',
    name_i18n: I18N(
      'Стандарт ACME — 14 грейдов',
      'ACME стандарти — 14 грейд',
      'ACME standarti — 14 greyd',
      'ACME Standard — 14 Grades',
    ),
    description_i18n: I18N(
      'Стандартная 14-грейдовая шкала ACME.',
      'ACME 14-грейдли стандарт шкаласи.',
      "ACME 14-greydli standart shkalasi.",
      'ACME standard 14-grade scale.',
    ),
    structure_type: 'GRADE_14',
    status: 'LOCKED',
    gap_policy: 'STRICT_NO_GAPS',
    version_number: 1,
    parent_structure_id: null,
    approved_at: '2026-04-20T10:00:00Z',
    approved_by: '7e9c1234-5678-90ab-cdef-1234567890ab',
    approved_by_name: 'Dilshod Karimov',
    locked_at: '2026-04-20T10:30:00Z',
    locked_by: '7e9c1234-5678-90ab-cdef-1234567890ab',
    locked_by_name: 'Dilshod Karimov',
    created_at: '2026-02-10T10:00:00Z',
    updated_at: '2026-04-20T10:30:00Z',
    grades: acmeGrades,
  },
  {
    id: betaStructureId,
    project_id: 'proj-beta-univ',
    code: 'BETA-16',
    name_i18n: I18N(
      'Beta University — 16 грейдов',
      'Beta университет — 16 грейд',
      'Beta universitet — 16 greyd',
      'Beta University — 16 Grades',
    ),
    structure_type: 'GRADE_16',
    status: 'APPROVED',
    gap_policy: 'ALLOW_GAPS_WARN',
    version_number: 1,
    parent_structure_id: null,
    approved_at: '2026-04-25T09:00:00Z',
    approved_by: '7e9c1234-5678-90ab-cdef-1234567890ac',
    approved_by_name: 'Nodira Yusupova',
    created_at: '2026-03-01T09:00:00Z',
    updated_at: '2026-04-25T09:00:00Z',
    grades: betaGrades,
  },
];

// Realistic position-distribution per grade for the demo pyramid. Bell-shaped.
const acmePyramidCounts: number[] = [3, 5, 8, 10, 12, 14, 12, 9, 7, 5, 3, 2, 1, 1];
const betaPyramidCounts: number[] = [2, 4, 6, 8, 10, 12, 14, 13, 11, 9, 7, 5, 3, 2, 1, 1];

const evaluations: MockEvaluation[] = [
  {
    id: cfoEvaluationId,
    project_id: 'proj-acme-2026',
    position_id: 'pos-cfo',
    methodology_version_id: cfoV1Id,
    evaluator_user_id: 'mock-evaluator-1',
    // Phase 6 demo: APPROVED so the AssignedGradeBadge is meaningful.
    // raw_total ~75 lands in G2 of the ACME 14-grade structure.
    status: 'APPROVED',
    raw_total_score: cfoRawTotal,
    displayed_total_score: Number(cfoRawTotal.toFixed(2)),
    grade_band_id: `${acmeStructureId}-g-2-band`,
    assigned_grade_number: 2,
    submitted_at: '2026-04-20T08:00:00Z',
    submitted_by: 'mock-evaluator-1',
    approved_at: '2026-04-22T10:00:00Z',
    approved_by: 'mock-approver-1',
    locked_at: null,
  },
  {
    id: sweEvaluationId,
    project_id: 'proj-acme-2026',
    position_id: 'pos-swe-senior',
    methodology_version_id: cfoV1Id,
    evaluator_user_id: 'mock-evaluator-1',
    status: 'SUBMITTED',
    raw_total_score: sweRawTotal,
    displayed_total_score: Number(sweRawTotal.toFixed(2)),
    submitted_at: '2026-05-15T08:00:00Z',
    submitted_by: 'mock-evaluator-1',
  },
];

const evaluationScores: MockEvaluationScore[] = [
  ...cfoEvaluationScores,
  ...sweEvaluationScores,
];

const calibrationEvents: MockCalibrationEvent[] = [];

// ============================================================
// MVP 2 Phase 1 — Approval + Comment fixtures
// ============================================================

export type MockApprovalRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'CANCELLED';

export type MockApprovalStepStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';

export type MockApprovalEntityType =
  | 'JOB_PROFILE'
  | 'METHODOLOGY_VERSION'
  | 'EVALUATION'
  | 'GRADE_STRUCTURE'
  | 'PROJECT';

export interface MockApprovalStep {
  id: string;
  approvalRequestId: string;
  stepOrder: number;
  approverUserId: string | null;
  approverName: string | null;
  requiredPermission: string | null;
  status: MockApprovalStepStatus;
  decidedAt: string | null;
  decidedByUserId: string | null;
  decidedByName: string | null;
  notes: string | null;
  reason: string | null;
}

export interface MockApprovalDecision {
  id: string;
  approvalRequestId: string;
  approvalStepId: string;
  decision: 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
  decidedByUserId: string;
  decidedByName: string;
  decidedAt: string;
  notes: string | null;
  reason: string | null;
}

export interface MockApprovalRequest {
  id: string;
  projectId: string;
  entityType: MockApprovalEntityType;
  entityId: string;
  entityLabel: Partial<Record<Locale, string>>;
  status: MockApprovalRequestStatus;
  initiatedByUserId: string;
  initiatedByName: string;
  initiatedAt: string;
  currentStepOrder: number | null;
  totalSteps: number;
  notesI18n: Partial<Record<Locale, string>>;
  steps: MockApprovalStep[];
  decisions: MockApprovalDecision[];
  completedAt: string | null;
}

const SUPER_ADMIN_ID = '00000000-0000-0000-0000-000000000001';

const approvalRequests: MockApprovalRequest[] = [
  // 1) CFO Job Profile awaiting approval — current super-admin is approver
  {
    id: 'appr-cfo-jp-1',
    projectId: 'proj-acme-2026',
    entityType: 'JOB_PROFILE',
    entityId: 'jp-cfo-v1',
    entityLabel: I18N(
      'Должностной профиль: Финансовый директор',
      'Лавозим профили: Молия директори',
      'Lavozim profili: Moliya direktori',
      'Job profile: Chief Financial Officer',
    ),
    status: 'PENDING',
    initiatedByUserId: 'mock-evaluator-1',
    initiatedByName: 'HRLab Consultant',
    initiatedAt: '2026-04-20T09:00:00Z',
    currentStepOrder: 1,
    totalSteps: 1,
    notesI18n: I18N(
      'Прошу проверить и утвердить профиль.',
      'Профайлни кўриб чиқиб тасдиқлашингизни сўрайман.',
      "Profilni ko'rib chiqib tasdiqlashingizni so'rayman.",
      'Please review and approve this profile.',
    ),
    steps: [
      {
        id: 'appr-cfo-jp-1-step-1',
        approvalRequestId: 'appr-cfo-jp-1',
        stepOrder: 1,
        approverUserId: SUPER_ADMIN_ID,
        approverName: 'Dev User',
        requiredPermission: 'JOB_PROFILE_APPROVE',
        status: 'PENDING',
        decidedAt: null,
        decidedByUserId: null,
        decidedByName: null,
        notes: null,
        reason: null,
      },
    ],
    decisions: [],
    completedAt: null,
  },
  // 2) Senior SWE evaluation awaiting approval
  {
    id: 'appr-swe-eval-1',
    projectId: 'proj-acme-2026',
    entityType: 'EVALUATION',
    entityId: 'eval-swe-1',
    entityLabel: I18N(
      'Оценка: Senior Software Engineer',
      'Баҳолаш: Senior Software Engineer',
      'Baholash: Senior Software Engineer',
      'Evaluation: Senior Software Engineer',
    ),
    status: 'PENDING',
    initiatedByUserId: 'mock-evaluator-1',
    initiatedByName: 'HRLab Consultant',
    initiatedAt: '2026-05-15T08:30:00Z',
    currentStepOrder: 1,
    totalSteps: 2,
    notesI18n: I18N(
      'Оценка готова к утверждению комитетом.',
      'Баҳолаш қўмита томонидан тасдиқланишга тайёр.',
      "Baholash qo'mita tomonidan tasdiqlanishga tayyor.",
      'Evaluation ready for committee approval.',
    ),
    steps: [
      {
        id: 'appr-swe-eval-1-step-1',
        approvalRequestId: 'appr-swe-eval-1',
        stepOrder: 1,
        approverUserId: SUPER_ADMIN_ID,
        approverName: 'Dev User',
        requiredPermission: 'EVALUATION_APPROVE',
        status: 'PENDING',
        decidedAt: null,
        decidedByUserId: null,
        decidedByName: null,
        notes: null,
        reason: null,
      },
      {
        id: 'appr-swe-eval-1-step-2',
        approvalRequestId: 'appr-swe-eval-1',
        stepOrder: 2,
        approverUserId: null,
        approverName: null,
        requiredPermission: 'EVALUATION_LOCK',
        status: 'PENDING',
        decidedAt: null,
        decidedByUserId: null,
        decidedByName: null,
        notes: null,
        reason: null,
      },
    ],
    decisions: [],
    completedAt: null,
  },
];

export type MockCommentEntityType = MockApprovalEntityType | 'POSITION' | 'DEPARTMENT';

export interface MockCommentMention {
  userId: string;
  userName: string | null;
}

export interface MockComment {
  id: string;
  entityType: MockCommentEntityType;
  entityId: string;
  parentCommentId: string | null;
  authorUserId: string;
  authorName: string;
  body: string;
  createdAt: string;
  updatedAt: string | null;
  deletedAt: string | null;
  mentions: MockCommentMention[];
}

const comments: MockComment[] = [
  {
    id: 'comm-cfo-1',
    entityType: 'JOB_PROFILE',
    entityId: 'jp-cfo-v1',
    parentCommentId: null,
    authorUserId: 'mock-evaluator-1',
    authorName: 'HRLab Consultant',
    body: 'Раздел «Основные обязанности» нужно дополнить пунктом про управление казначейством. @[00000000-0000-0000-0000-000000000001|Dev User] просьба проверить.',
    createdAt: '2026-04-18T10:00:00Z',
    updatedAt: null,
    deletedAt: null,
    mentions: [{ userId: SUPER_ADMIN_ID, userName: 'Dev User' }],
  },
  {
    id: 'comm-cfo-2',
    entityType: 'JOB_PROFILE',
    entityId: 'jp-cfo-v1',
    parentCommentId: null,
    authorUserId: SUPER_ADMIN_ID,
    authorName: 'Dev User',
    body: 'KPI хорошо сформулированы, но нужно уточнить целевые значения по EBITDA и cash conversion cycle.',
    createdAt: '2026-04-19T11:00:00Z',
    updatedAt: null,
    deletedAt: null,
    mentions: [],
  },
  {
    id: 'comm-cfo-3',
    entityType: 'JOB_PROFILE',
    entityId: 'jp-cfo-v1',
    parentCommentId: 'comm-cfo-1',
    authorUserId: SUPER_ADMIN_ID,
    authorName: 'Dev User',
    body: 'Принято, добавим раздел про казначейство в новую редакцию.',
    createdAt: '2026-04-19T11:05:00Z',
    updatedAt: null,
    deletedAt: null,
    mentions: [],
  },
  {
    id: 'comm-eval-1',
    entityType: 'EVALUATION',
    entityId: 'eval-cfo-1',
    parentCommentId: null,
    authorUserId: 'mock-evaluator-1',
    authorName: 'HRLab Consultant',
    body: 'Калибровочный комитет рекомендует пересмотреть оценку фактора «Влияние» — текущий уровень кажется заниженным.',
    createdAt: '2026-04-22T14:00:00Z',
    updatedAt: null,
    deletedAt: null,
    mentions: [],
  },
];

// ============================================================
// MVP 2 Phase 2 — Import / Export fixtures
// ============================================================

export type MockImportBatchStatus =
  | 'UPLOADED'
  | 'SCANNING'
  | 'SCAN_FAILED'
  | 'PARSING'
  | 'VALIDATING'
  | 'VALIDATION_FAILED'
  | 'READY_FOR_REVIEW'
  | 'READY_TO_COMMIT'
  | 'COMMITTING'
  | 'COMMITTED'
  | 'PARTIALLY_COMMITTED'
  | 'FAILED'
  | 'CANCELLED'
  | 'ARCHIVED';

export type MockImportErrorLevel = 'BLOCKER' | 'ERROR' | 'WARNING' | 'INFO';

export interface MockImportBatch {
  id: string;
  projectId: string | null;
  templateCode: string;
  status: MockImportBatchStatus;
  originalFilename: string;
  fileSize: number;
  fileChecksum?: string | null;
  totalRowCount?: number | null;
  errorRowCount?: number | null;
  warningRowCount?: number | null;
  committedRowCount?: number | null;
  containsSalaryData: boolean;
  uploadedBy?: string | null;
  uploadedAt: string;
  committedBy?: string | null;
  committedAt?: string | null;
  traceId?: string | null;
}

export interface MockImportError {
  id: string;
  importBatchId: string;
  importBatchRowId?: string | null;
  errorLevel: MockImportErrorLevel;
  errorCode: string;
  fieldName?: string | null;
  message: string;
  suggestedFix?: string | null;
  rowNumber?: number | null;
}

export type MockExportJobStatus =
  | 'REQUESTED'
  | 'QUEUED'
  | 'GENERATING'
  | 'GENERATED'
  | 'FAILED'
  | 'DOWNLOADED'
  | 'EXPIRED'
  | 'CANCELLED';

export interface MockExportJob {
  id: string;
  projectId: string;
  exportType: string;
  format: 'XLSX' | 'CSV' | 'PDF' | 'DOCX';
  status: MockExportJobStatus;
  requestedBy?: string | null;
  requestedAt: string;
  generatedAt?: string | null;
  expiresAt?: string | null;
  downloadedAt?: string | null;
  rowCount?: number | null;
  fileSize?: number | null;
  containsSalaryData: boolean;
  containsPersonalData: boolean;
  signedUrl?: string | null;
}

const importBatches: MockImportBatch[] = [
  {
    id: 'imp-org-1',
    projectId: 'proj-acme-2026',
    templateCode: 'ORG_STRUCTURE_V1',
    status: 'READY_FOR_REVIEW',
    originalFilename: 'acme_org_structure_2026.xlsx',
    fileSize: 84321,
    fileChecksum: 'sha256:demo-checksum',
    totalRowCount: 12,
    errorRowCount: 0,
    warningRowCount: 2,
    committedRowCount: 0,
    containsSalaryData: false,
    uploadedBy: '00000000-0000-0000-0000-000000000001',
    uploadedAt: '2026-05-22T09:30:00Z',
    traceId: 'trace-imp-org-1',
  },
];

const importErrors: MockImportError[] = [
  {
    id: 'err-org-1-w1',
    importBatchId: 'imp-org-1',
    importBatchRowId: 'row-3',
    errorLevel: 'WARNING',
    errorCode: 'PARENT_HIERARCHY_DEEP',
    fieldName: 'parentCode',
    message: 'Подразделение находится на 5 уровне иерархии — рекомендуется не глубже 4.',
    suggestedFix: 'Пересмотрите иерархию подразделений.',
    rowNumber: 3,
  },
  {
    id: 'err-org-1-w2',
    importBatchId: 'imp-org-1',
    importBatchRowId: 'row-7',
    errorLevel: 'WARNING',
    errorCode: 'NAME_NOT_LOCALIZED',
    fieldName: 'name.uz-Cyrl-UZ',
    message: 'Поле name.uz-Cyrl-UZ пустое — будет применен fallback на ru-RU.',
    suggestedFix: 'Добавьте перевод на узбекский (кириллица).',
    rowNumber: 7,
  },
];

const exportJobs: MockExportJob[] = [
  {
    id: 'exp-eval-1',
    projectId: 'proj-acme-2026',
    exportType: 'EVALUATION_MATRIX',
    format: 'XLSX',
    status: 'GENERATED',
    requestedBy: '00000000-0000-0000-0000-000000000001',
    requestedAt: '2026-05-22T10:00:00Z',
    generatedAt: '2026-05-22T10:00:08Z',
    expiresAt: '2026-05-29T10:00:08Z',
    rowCount: 42,
    fileSize: 56234,
    containsSalaryData: false,
    containsPersonalData: false,
    signedUrl: 'https://mock-storage.local/exports/exp-eval-1/result.xlsx?sig=stub',
  },
];

// ============================================================
// MVP 2 Phase 3 — Report fixtures (Reports Center)
// Mirrors backend ReportResponse DTO shape — see architecture §17.
// ============================================================

export type MockReportStatus =
  | 'REQUESTED'
  | 'QUEUED'
  | 'GENERATING'
  | 'GENERATED'
  | 'FAILED'
  | 'DOWNLOADED'
  | 'EXPIRED'
  | 'CANCELLED';

export type MockReportType =
  | 'GRADE_DISTRIBUTION'
  | 'POSITION_CATALOG'
  | 'EVALUATION_SUMMARY'
  | 'METHODOLOGY_SPEC'
  | 'AUDIT_SUMMARY'
  | 'EXECUTIVE_SUMMARY';

export type MockReportFormat = 'PDF' | 'DOCX' | 'XLSX';

export interface MockReport {
  id: string;
  projectId: string;
  reportType: MockReportType;
  format: MockReportFormat;
  status: MockReportStatus;
  title: string | null;
  locale: string | null;
  requestedBy: string | null;
  requestedAt: string;
  generatedAt: string | null;
  expiresAt: string | null;
  downloadedAt: string | null;
  fileSize: number | null;
  containsSalaryData: boolean;
  containsPersonalData: boolean;
  attemptCount: number;
  failureReason: string | null;
  traceId: string | null;
  /** MSW-only internal pointer to the storage object. */
  signedUrl?: string | null;
}

const reports: MockReport[] = [
  {
    id: 'rep-grade-dist-1',
    projectId: 'proj-acme-2026',
    reportType: 'GRADE_DISTRIBUTION',
    format: 'PDF',
    status: 'GENERATED',
    title: 'Grade distribution — ACME Grading 2026',
    locale: 'ru-RU',
    requestedBy: '00000000-0000-0000-0000-000000000001',
    requestedAt: '2026-05-23T08:15:00Z',
    generatedAt: '2026-05-23T08:15:12Z',
    expiresAt: '2026-06-06T08:15:12Z',
    downloadedAt: null,
    fileSize: 184273,
    containsSalaryData: false,
    containsPersonalData: false,
    attemptCount: 1,
    failureReason: null,
    traceId: 'trace-rep-grade-dist-1',
    signedUrl:
      'https://mock-storage.local/reports/rep-grade-dist-1/result.pdf?sig=stub',
  },
  {
    id: 'rep-eval-summary-1',
    projectId: 'proj-acme-2026',
    reportType: 'EVALUATION_SUMMARY',
    format: 'DOCX',
    status: 'GENERATED',
    title: 'Evaluation summary — ACME Q1 2026',
    locale: 'ru-RU',
    requestedBy: '00000000-0000-0000-0000-000000000001',
    requestedAt: '2026-05-23T09:00:00Z',
    generatedAt: '2026-05-23T09:00:18Z',
    expiresAt: '2026-06-06T09:00:18Z',
    downloadedAt: null,
    fileSize: 92841,
    containsSalaryData: false,
    containsPersonalData: false,
    attemptCount: 1,
    failureReason: null,
    traceId: 'trace-rep-eval-summary-1',
    signedUrl:
      'https://mock-storage.local/reports/rep-eval-summary-1/result.docx?sig=stub',
  },
  {
    id: 'rep-method-spec-1',
    projectId: 'proj-acme-2026',
    reportType: 'METHODOLOGY_SPEC',
    format: 'PDF',
    status: 'GENERATING',
    title: 'Methodology spec — CFO Finance v1',
    locale: 'ru-RU',
    requestedBy: '00000000-0000-0000-0000-000000000001',
    requestedAt: '2026-05-23T10:30:00Z',
    generatedAt: null,
    expiresAt: null,
    downloadedAt: null,
    fileSize: null,
    containsSalaryData: false,
    containsPersonalData: false,
    attemptCount: 1,
    failureReason: null,
    traceId: 'trace-rep-method-spec-1',
    signedUrl: null,
  },
];

// ============================================================
// Bulk Evaluation by Factor — K-sheet demo seed
// ============================================================
//
// Generates a realistic set of positions + evaluations so the
// `?mode=by-factor` view has enough rows for pagination + bulk
// action demos (5 extra departments + 30 extra positions, each
// with an empty evaluation under the CFO methodology v1).
// All ids are namespaced `pos-ks-…` / `eval-ks-…` to avoid any
// collision with the curated CFO/SWE fixtures above.
const ksDepartments: MockDepartment[] = [
  {
    id: 'dep-acme-ops',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'OPS',
    name_i18n: { 'ru-RU': 'Операции', 'en-US': 'Operations', 'uz-Cyrl-UZ': 'Операциялар', 'uz-Latn-UZ': 'Operatsiyalar' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
    updated_at: '2026-02-15T10:00:00Z',
  },
  {
    id: 'dep-acme-hr',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'HR',
    name_i18n: { 'ru-RU': 'HR', 'en-US': 'HR', 'uz-Cyrl-UZ': 'HR', 'uz-Latn-UZ': 'HR' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
    updated_at: '2026-02-16T10:00:00Z',
  },
  {
    id: 'dep-acme-sales',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'SALES',
    name_i18n: { 'ru-RU': 'Продажи', 'en-US': 'Sales', 'uz-Cyrl-UZ': 'Сотувлар', 'uz-Latn-UZ': 'Sotuvlar' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
    updated_at: '2026-02-17T10:00:00Z',
  },
  {
    id: 'dep-acme-marketing',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'MKT',
    name_i18n: { 'ru-RU': 'Маркетинг', 'en-US': 'Marketing', 'uz-Cyrl-UZ': 'Маркетинг', 'uz-Latn-UZ': 'Marketing' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
    updated_at: '2026-02-18T10:00:00Z',
  },
  {
    id: 'dep-acme-legal',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'LEG',
    name_i18n: { 'ru-RU': 'Юридический', 'en-US': 'Legal', 'uz-Cyrl-UZ': 'Юридик', 'uz-Latn-UZ': 'Yuridik' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
    updated_at: '2026-02-19T10:00:00Z',
  },
];

const ksPositionSpecs: { code: string; dept: string; title: [string, string, string, string] }[] = [
  { code: 'OPS-COO', dept: 'dep-acme-ops', title: ['Операционный директор', 'Операцион директор', 'Operatsion direktor', 'Chief Operating Officer'] },
  { code: 'OPS-HEAD', dept: 'dep-acme-ops', title: ['Начальник отдела операций', 'Операциялар бўлим бошлиғи', 'Operatsiyalar boʻlim boshligʻi', 'Head of Operations'] },
  { code: 'OPS-SPEC', dept: 'dep-acme-ops', title: ['Специалист операций', 'Операциялар мутахассиси', 'Operatsiyalar mutaxassisi', 'Operations Specialist'] },
  { code: 'OPS-ANALYST', dept: 'dep-acme-ops', title: ['Аналитик операций', 'Операциялар таҳлилчиси', 'Operatsiyalar tahlilchisi', 'Operations Analyst'] },
  { code: 'OPS-COORD', dept: 'dep-acme-ops', title: ['Координатор операций', 'Операциялар координатори', 'Operatsiyalar koordinatori', 'Operations Coordinator'] },
  { code: 'OPS-MGR', dept: 'dep-acme-ops', title: ['Менеджер операций', 'Операциялар менежери', 'Operatsiyalar menejeri', 'Operations Manager'] },
  { code: 'HR-CHRO', dept: 'dep-acme-hr', title: ['Директор по персоналу', 'Кадрлар директори', 'Kadrlar direktori', 'Chief HR Officer'] },
  { code: 'HR-LEAD', dept: 'dep-acme-hr', title: ['Руководитель HR', 'HR раҳбари', 'HR rahbari', 'HR Lead'] },
  { code: 'HR-REC', dept: 'dep-acme-hr', title: ['Рекрутёр', 'Рекрутёр', 'Rekruyter', 'Recruiter'] },
  { code: 'HR-BP', dept: 'dep-acme-hr', title: ['HR бизнес-партнёр', 'HR бизнес шериги', 'HR biznes sherigi', 'HR Business Partner'] },
  { code: 'HR-COMP', dept: 'dep-acme-hr', title: ['Специалист по C&B', 'C&B мутахассиси', 'C&B mutaxassisi', 'Compensation Specialist'] },
  { code: 'HR-LD', dept: 'dep-acme-hr', title: ['Специалист по обучению', 'Ўқитиш мутахассиси', 'Oʻqitish mutaxassisi', 'L&D Specialist'] },
  { code: 'SAL-VP', dept: 'dep-acme-sales', title: ['Вице-президент по продажам', 'Сотувлар бўйича вице-президент', 'Sotuvlar boʻyicha vitse-prezident', 'VP of Sales'] },
  { code: 'SAL-DIR', dept: 'dep-acme-sales', title: ['Директор по продажам', 'Сотувлар директори', 'Sotuvlar direktori', 'Sales Director'] },
  { code: 'SAL-MGR', dept: 'dep-acme-sales', title: ['Менеджер по продажам', 'Сотувлар менежери', 'Sotuvlar menejeri', 'Sales Manager'] },
  { code: 'SAL-SR', dept: 'dep-acme-sales', title: ['Старший менеджер по продажам', 'Катта сотувлар менежери', 'Katta sotuvlar menejeri', 'Senior Sales Manager'] },
  { code: 'SAL-REP', dept: 'dep-acme-sales', title: ['Менеджер по работе с клиентами', 'Мижозлар билан ишлаш менежери', 'Mijozlar bilan ishlash menejeri', 'Account Executive'] },
  { code: 'SAL-DEV', dept: 'dep-acme-sales', title: ['Менеджер по развитию бизнеса', 'Бизнесни ривожлантириш менежери', 'Biznesni rivojlantirish menejeri', 'Business Development Mgr'] },
  { code: 'MKT-CMO', dept: 'dep-acme-marketing', title: ['Директор по маркетингу', 'Маркетинг директори', 'Marketing direktori', 'Chief Marketing Officer'] },
  { code: 'MKT-HEAD', dept: 'dep-acme-marketing', title: ['Руководитель маркетинга', 'Маркетинг раҳбари', 'Marketing rahbari', 'Head of Marketing'] },
  { code: 'MKT-BRAND', dept: 'dep-acme-marketing', title: ['Бренд-менеджер', 'Бренд менежери', 'Brend menejeri', 'Brand Manager'] },
  { code: 'MKT-CONT', dept: 'dep-acme-marketing', title: ['Контент-менеджер', 'Контент менежери', 'Kontent menejeri', 'Content Manager'] },
  { code: 'MKT-DESIGN', dept: 'dep-acme-marketing', title: ['Дизайнер', 'Дизайнер', 'Dizayner', 'Designer'] },
  { code: 'MKT-SMM', dept: 'dep-acme-marketing', title: ['SMM-менеджер', 'SMM менежери', 'SMM menejeri', 'SMM Manager'] },
  { code: 'LEG-GC', dept: 'dep-acme-legal', title: ['Главный юрисконсульт', 'Бош юрисконсульт', 'Bosh yuriskonsult', 'General Counsel'] },
  { code: 'LEG-HEAD', dept: 'dep-acme-legal', title: ['Руководитель юр. отдела', 'Юридик бўлим раҳбари', 'Yuridik boʻlim rahbari', 'Head of Legal'] },
  { code: 'LEG-LAWYER', dept: 'dep-acme-legal', title: ['Юрист', 'Юрист', 'Yurist', 'Lawyer'] },
  { code: 'LEG-SR', dept: 'dep-acme-legal', title: ['Старший юрист', 'Катта юрист', 'Katta yurist', 'Senior Lawyer'] },
  { code: 'LEG-COMP', dept: 'dep-acme-legal', title: ['Специалист по комплаенсу', 'Комплаенс мутахассиси', 'Kompleyans mutaxassisi', 'Compliance Specialist'] },
  { code: 'LEG-PARA', dept: 'dep-acme-legal', title: ['Паралегал', 'Параюрист', 'Parayurist', 'Paralegal'] },
];

const ksPositions: MockPosition[] = ksPositionSpecs.map((p, idx) => ({
  id: `pos-ks-${p.code.toLowerCase()}`,
  project_id: 'proj-acme-2026',
  department_id: p.dept,
  code: p.code,
  title_i18n: { 'ru-RU': p.title[0], 'uz-Cyrl-UZ': p.title[1], 'uz-Latn-UZ': p.title[2], 'en-US': p.title[3] },
  function: p.dept.replace('dep-acme-', ''),
  category: idx % 5 === 0 ? 'Senior Manager' : 'Individual contributor',
  job_family: p.dept.replace('dep-acme-', '').toUpperCase(),
  job_level: `L${3 + (idx % 7)}`,
  status: 'ACTIVE',
  updated_at: '2026-05-20T08:00:00Z',
}));

const ksEvaluations: MockEvaluation[] = ksPositions.map((pos, idx) => ({
  id: `eval-ks-${pos.code.toLowerCase()}`,
  project_id: 'proj-acme-2026',
  position_id: pos.id,
  methodology_version_id: cfoV1Id,
  evaluator_user_id: 'mock-evaluator-1',
  // Mix of statuses for demo realism. Most stay DRAFT/INCOMPLETE so the
  // K-sheet selects and bulk actions have plenty of editable rows.
  status: idx % 11 === 0 ? 'LOCKED' : idx % 7 === 0 ? 'APPROVED' : idx % 5 === 0 ? 'SUBMITTED' : 'DRAFT',
  raw_total_score: 0,
  displayed_total_score: 0,
}));

// Pre-fill 1/3 of the K-sheet evaluations with a scattering of saved
// factor scores so the progress chip and "only unfilled" filter both
// have something to demo. We scatter only across the first 3 factors.
const ksEvaluationScores: MockEvaluationScore[] = [];
ksEvaluations.forEach((ev, evIdx) => {
  if (evIdx % 3 !== 0) return;
  const version = methodologyVersions.find((v) => v.id === ev.methodology_version_id);
  if (!version) return;
  version.factors.slice(0, 3).forEach((f, fIdx) => {
    const lvl = f.levels[(evIdx + fIdx) % f.levels.length];
    ksEvaluationScores.push({
      id: `evscore-ks-${ev.id}-${f.code}`,
      evaluation_id: ev.id,
      factor_id: f.id,
      factor_level_id: lvl.id,
      raw_factor_score: Number((f.weight * (lvl.points / 100)).toFixed(4)),
      comment_text: null,
      manually_adjusted: false,
    });
  });
});

export const mockDb = {
  projects: [...projects],
  departments: [...departments, ...ksDepartments],
  positions: [...positions, ...ksPositions],
  workflowProgress,
  jobProfiles: [...jobProfiles],
  questionnaires: [...questionnaires],
  questionnaireTemplates,
  methodologies: [...methodologies],
  methodologyVersions: [...methodologyVersions],
  methodologyTemplates,
  evaluations: [...evaluations, ...ksEvaluations],
  evaluationScores: [...evaluationScores, ...ksEvaluationScores],
  calibrationEvents: [...calibrationEvents],
  gradeStructures: [...gradeStructures],
  gradePyramidCounts: {
    [acmeStructureId]: acmePyramidCounts,
    [betaStructureId]: betaPyramidCounts,
  } as Record<string, number[]>,
  approvalRequests: [...approvalRequests],
  comments: [...comments],
  importBatches: [...importBatches],
  importErrors: [...importErrors],
  exportJobs: [...exportJobs],
  reports: [...reports],
};
