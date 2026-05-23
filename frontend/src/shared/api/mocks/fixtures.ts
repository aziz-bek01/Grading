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
  tenant_id: string;
  code: string;
  name: Partial<Record<Locale, string>>;
  description?: string;
  status: MockProjectStatus;
  start_date?: string;
  end_date?: string;
  updated_at: string;
}

export interface MockDepartment {
  id: string;
  project_id: string;
  parent_id: string | null;
  code: string;
  name: Partial<Record<Locale, string>>;
  type: MockDepartmentType;
  status: MockEntityStatus;
  updated_at: string;
}

export interface MockPosition {
  id: string;
  project_id: string;
  department_id: string;
  code: string;
  title: Partial<Record<Locale, string>>;
  function?: string;
  category?: string;
  job_family?: string;
  job_level?: string;
  status: MockEntityStatus;
  updated_at: string;
}

export interface MockStage {
  key: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'BLOCKED' | 'LOCKED_FUTURE';
  completion_percent: number;
  total_items?: number;
  completed_items?: number;
  responsible_role?: string;
  blockers: { code: string; count: number }[];
}

const projects: MockProject[] = [
  {
    id: 'proj-acme-2026',
    tenant_id: 'tenant-acme',
    code: 'ACME-2026',
    name: {
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
    tenant_id: 'tenant-acme',
    code: 'ACME-PILOT',
    name: {
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
    tenant_id: 'tenant-beta',
    code: 'BETA-2026',
    name: {
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
    name: { 'ru-RU': 'Головной офис', 'en-US': 'Headquarters', 'uz-Cyrl-UZ': 'Бош офис', 'uz-Latn-UZ': 'Bosh ofis' },
    type: 'BRANCH',
    status: 'ACTIVE',
    updated_at: '2026-02-10T10:00:00Z',
  },
  {
    id: 'dep-acme-fin',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'FIN',
    name: { 'ru-RU': 'Финансы', 'en-US': 'Finance', 'uz-Cyrl-UZ': 'Молия', 'uz-Latn-UZ': 'Moliya' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
    updated_at: '2026-02-12T10:00:00Z',
  },
  {
    id: 'dep-acme-fin-treasury',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-fin',
    code: 'FIN-TR',
    name: { 'ru-RU': 'Казначейство', 'en-US': 'Treasury', 'uz-Cyrl-UZ': 'Хазина', 'uz-Latn-UZ': 'Xazina' },
    type: 'UNIT',
    status: 'ACTIVE',
    updated_at: '2026-02-14T10:00:00Z',
  },
  {
    id: 'dep-acme-it',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-hq',
    code: 'IT',
    name: { 'ru-RU': 'ИТ', 'en-US': 'IT', 'uz-Cyrl-UZ': 'АТ', 'uz-Latn-UZ': 'AT' },
    type: 'DIVISION',
    status: 'ACTIVE',
    updated_at: '2026-03-01T10:00:00Z',
  },
  {
    id: 'dep-acme-it-legacy',
    project_id: 'proj-acme-2026',
    parent_id: 'dep-acme-it',
    code: 'IT-LEG',
    name: { 'ru-RU': 'Поддержка legacy', 'en-US': 'Legacy Support', 'uz-Cyrl-UZ': 'Эски тизим', 'uz-Latn-UZ': 'Eski tizim' },
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
    title: { 'ru-RU': 'Финансовый директор', 'en-US': 'Chief Financial Officer', 'uz-Cyrl-UZ': 'Молия директори', 'uz-Latn-UZ': 'Moliya direktori' },
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
    title: { 'ru-RU': 'Руководитель казначейства', 'en-US': 'Head of Treasury', 'uz-Cyrl-UZ': 'Хазина бошлиғи', 'uz-Latn-UZ': 'Xazina boshligʻi' },
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
    title: { 'ru-RU': 'Технический директор', 'en-US': 'Chief Technology Officer', 'uz-Cyrl-UZ': 'Технологиялар директори', 'uz-Latn-UZ': 'Texnologiyalar direktori' },
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
    title: { 'ru-RU': 'Старший разработчик', 'en-US': 'Senior Software Engineer', 'uz-Cyrl-UZ': 'Катта дастурчи', 'uz-Latn-UZ': 'Katta dasturchi' },
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
    title: { 'ru-RU': 'Разработчик', 'en-US': 'Software Engineer', 'uz-Cyrl-UZ': 'Дастурчи', 'uz-Latn-UZ': 'Dasturchi' },
    function: 'Technology',
    category: 'Individual contributor',
    job_family: 'IT',
    job_level: 'L5',
    status: 'DRAFT',
    updated_at: '2026-05-15T08:00:00Z',
  },
];

const workflowProgress: Record<string, MockStage[]> = {
  'proj-acme-2026': [
    { key: 'SETUP', status: 'COMPLETE', completion_percent: 100, blockers: [] },
    { key: 'ORGANIZATION', status: 'COMPLETE', completion_percent: 100, total_items: 5, completed_items: 5, responsible_role: 'HRLAB_ANALYST', blockers: [] },
    { key: 'POSITIONS', status: 'IN_PROGRESS', completion_percent: 60, total_items: 5, completed_items: 3, responsible_role: 'HRLAB_ANALYST', blockers: [{ code: 'POSITION_MISSING_DEPARTMENT', count: 1 }] },
    { key: 'JOB_PROFILES', status: 'PENDING', completion_percent: 0, blockers: [] },
    { key: 'METHODOLOGY', status: 'IN_PROGRESS', completion_percent: 40, blockers: [] },
    { key: 'EVALUATION', status: 'PENDING', completion_percent: 0, blockers: [] },
    { key: 'CALIBRATION', status: 'LOCKED_FUTURE', completion_percent: 0, blockers: [] },
    { key: 'GRADES', status: 'PENDING', completion_percent: 0, blockers: [] },
    { key: 'COMPENSATION', status: 'LOCKED_FUTURE', completion_percent: 0, blockers: [] },
    { key: 'REPORTS', status: 'LOCKED_FUTURE', completion_percent: 0, blockers: [] },
    { key: 'ARCHIVE', status: 'LOCKED_FUTURE', completion_percent: 0, blockers: [] },
  ],
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
  parent_revision_id?: string | null;
  purpose: Partial<Record<Locale, string>>;
  main_duties: Partial<Record<Locale, string>>;
  responsibility_area: Partial<Record<Locale, string>>;
  authority: Partial<Record<Locale, string>>;
  kpi_expected_results: Partial<Record<Locale, string>>;
  education_requirements: Partial<Record<Locale, string>>;
  experience_requirements: Partial<Record<Locale, string>>;
  knowledge_skills: Partial<Record<Locale, string>>;
  internal_interactions: Partial<Record<Locale, string>>;
  external_interactions: Partial<Record<Locale, string>>;
  working_conditions: Partial<Record<Locale, string>>;
  documents_regulations: Partial<Record<Locale, string>>;
  actualization_date?: string;
  created_at: string;
  updated_at: string;
  approved_at?: string | null;
  approved_by?: string | null;
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

const fullProfileLocaleSample = (ruPrefix: string): Partial<Record<Locale, string>> => ({
  'ru-RU': `${ruPrefix} (русский).`,
  'uz-Cyrl-UZ': `${ruPrefix} (ўзбек кирилл).`,
  'uz-Latn-UZ': `${ruPrefix} (oʻzbek lotin).`,
  'en-US': `${ruPrefix} (english).`,
});

const jobProfiles: MockJobProfile[] = [
  {
    id: 'jp-cfo-v1',
    position_id: 'pos-cfo',
    project_id: 'proj-acme-2026',
    status: 'APPROVED',
    revision_number: 1,
    parent_revision_id: null,
    purpose: fullProfileLocaleSample('Обеспечение финансовой устойчивости компании'),
    main_duties: fullProfileLocaleSample('Управление бюджетом, отчётность, контроль рисков'),
    responsibility_area: fullProfileLocaleSample('Все финансовые операции и отчётность'),
    authority: fullProfileLocaleSample('Утверждение бюджетов до 10 млн.'),
    kpi_expected_results: fullProfileLocaleSample('EBITDA, ROCE, точность отчётности'),
    education_requirements: fullProfileLocaleSample('Высшее экономическое'),
    experience_requirements: fullProfileLocaleSample('Не менее 10 лет в финансах'),
    knowledge_skills: fullProfileLocaleSample('МСФО, бюджетирование, риск-менеджмент'),
    internal_interactions: fullProfileLocaleSample('CEO, советы директоров, департаменты'),
    external_interactions: fullProfileLocaleSample('Аудиторы, банки, регуляторы'),
    working_conditions: fullProfileLocaleSample('Офис, гибкий график'),
    documents_regulations: fullProfileLocaleSample('Финансовый кодекс, МСФО, локальные акты'),
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
    parent_revision_id: null,
    purpose: { 'ru-RU': 'Разработка высоконагруженных backend сервисов.' },
    main_duties: { 'ru-RU': 'Проектирование, разработка, code review.' },
    responsibility_area: { 'ru-RU': 'Микросервисы платёжной платформы.' },
    authority: {},
    kpi_expected_results: { 'ru-RU': 'SLA, throughput, code coverage.' },
    education_requirements: { 'ru-RU': 'Высшее техническое.' },
    experience_requirements: { 'ru-RU': '5+ лет.' },
    knowledge_skills: { 'ru-RU': 'Java, Kotlin, Spring Boot, PostgreSQL.' },
    internal_interactions: {},
    external_interactions: {},
    working_conditions: {},
    documents_regulations: {},
    actualization_date: undefined,
    created_at: '2026-05-10T08:00:00Z',
    updated_at: '2026-05-12T08:00:00Z',
    approved_at: null,
    approved_by: null,
  },
];

const standardTemplate: MockQuestionnaireTemplate = {
  code: 'STANDARD_V1',
  name: {
    'ru-RU': 'Стандартный анализ должности',
    'uz-Cyrl-UZ': 'Стандарт лавозим тахлили',
    'uz-Latn-UZ': 'Standart lavozim tahlili',
    'en-US': 'Standard Job Analysis',
  },
  description: {
    'ru-RU': '8 базовых вопросов о должности.',
    'uz-Cyrl-UZ': 'Лавозим бўйича 8 та асосий савол.',
    'uz-Latn-UZ': 'Lavozim boʻyicha 8 ta asosiy savol.',
    'en-US': '8 baseline questions about the position.',
  },
  questions: [
    {
      id: 'q-std-1',
      code: 'PURPOSE',
      question_type: 'LONG_TEXT',
      prompt: { 'ru-RU': 'Опишите основную цель должности.', 'en-US': 'Describe the primary purpose of the role.' },
      required: true,
      sort_order: 1,
    },
    {
      id: 'q-std-2',
      code: 'DUTIES_COUNT',
      question_type: 'NUMBER',
      prompt: { 'ru-RU': 'Сколько основных обязанностей?', 'en-US': 'How many primary duties?' },
      required: true,
      sort_order: 2,
    },
    {
      id: 'q-std-3',
      code: 'DECISION_LEVEL',
      question_type: 'SINGLE_CHOICE',
      prompt: { 'ru-RU': 'Уровень принятия решений?', 'en-US': 'Decision-making level?' },
      required: true,
      sort_order: 3,
      choices: [
        { code: 'OPERATIONAL', label: { 'ru-RU': 'Операционный', 'en-US': 'Operational' } },
        { code: 'TACTICAL', label: { 'ru-RU': 'Тактический', 'en-US': 'Tactical' } },
        { code: 'STRATEGIC', label: { 'ru-RU': 'Стратегический', 'en-US': 'Strategic' } },
      ],
    },
    {
      id: 'q-std-4',
      code: 'INTERACTIONS',
      question_type: 'MULTI_CHOICE',
      prompt: { 'ru-RU': 'С кем регулярно взаимодействует?', 'en-US': 'Regular interactions with?' },
      required: false,
      sort_order: 4,
      choices: [
        { code: 'INTERNAL', label: { 'ru-RU': 'Внутренние', 'en-US': 'Internal' } },
        { code: 'EXTERNAL', label: { 'ru-RU': 'Внешние', 'en-US': 'External' } },
        { code: 'REGULATORS', label: { 'ru-RU': 'Регуляторы', 'en-US': 'Regulators' } },
      ],
    },
    {
      id: 'q-std-5',
      code: 'COMPLEXITY',
      question_type: 'RATING_SCALE',
      prompt: { 'ru-RU': 'Оцените сложность задач (1-5).', 'en-US': 'Rate task complexity (1-5).' },
      required: true,
      sort_order: 5,
      scale_min: 1,
      scale_max: 5,
    },
    {
      id: 'q-std-6',
      code: 'TITLE_ALT',
      question_type: 'TEXT',
      prompt: { 'ru-RU': 'Альтернативное название должности.', 'en-US': 'Alternative title.' },
      required: false,
      sort_order: 6,
    },
    {
      id: 'q-std-7',
      code: 'WORKING_CONDITIONS',
      question_type: 'LONG_TEXT',
      prompt: { 'ru-RU': 'Условия труда.', 'en-US': 'Working conditions.' },
      required: true,
      sort_order: 7,
    },
    {
      id: 'q-std-8',
      code: 'RISK_LEVEL',
      question_type: 'RATING_SCALE',
      prompt: { 'ru-RU': 'Уровень риска должности.', 'en-US': 'Risk level of the role.' },
      required: true,
      sort_order: 8,
      scale_min: 1,
      scale_max: 5,
    },
  ],
};

const executiveTemplate: MockQuestionnaireTemplate = {
  code: 'EXECUTIVE_V1',
  name: {
    'ru-RU': 'Анализ руководящей должности',
    'uz-Cyrl-UZ': 'Раҳбарлик лавозим тахлили',
    'uz-Latn-UZ': 'Rahbarlik lavozim tahlili',
    'en-US': 'Executive Job Analysis',
  },
  description: {
    'ru-RU': '12 вопросов для C-level / Senior Manager.',
    'uz-Cyrl-UZ': 'C-даражали лавозимлар учун 12 савол.',
    'uz-Latn-UZ': 'C-darajali lavozimlar uchun 12 savol.',
    'en-US': '12 questions for C-level / Senior Manager roles.',
  },
  questions: Array.from({ length: 12 }, (_, i) => ({
    id: `q-exec-${i + 1}`,
    code: `EXEC_${i + 1}`,
    question_type: (
      i % 4 === 0 ? 'LONG_TEXT' : i % 4 === 1 ? 'RATING_SCALE' : i % 4 === 2 ? 'SINGLE_CHOICE' : 'TEXT'
    ) as MockQuestionType,
    prompt: {
      'ru-RU': `Вопрос исполнительного уровня ${i + 1}.`,
      'en-US': `Executive question ${i + 1}.`,
    },
    required: i < 8,
    sort_order: i + 1,
    scale_min: 1,
    scale_max: 5,
    choices:
      i % 4 === 2
        ? [
            { code: 'LOW', label: { 'ru-RU': 'Низкий', 'en-US': 'Low' } },
            { code: 'MEDIUM', label: { 'ru-RU': 'Средний', 'en-US': 'Medium' } },
            { code: 'HIGH', label: { 'ru-RU': 'Высокий', 'en-US': 'High' } },
          ]
        : undefined,
  })),
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
      { question_id: 'q-exec-1', value: 'Финансовое лидерство и стратегия.' },
      { question_id: 'q-exec-2', value: 4 },
      { question_id: 'q-exec-3', value: 'HIGH' },
      { question_id: 'q-exec-4', value: 'CFO Executive' },
      { question_id: 'q-exec-5', value: 'Расширенный отчёт по EBITDA' },
    ],
    created_at: '2026-04-10T10:00:00Z',
    updated_at: '2026-05-01T10:00:00Z',
  },
];

export const mockDb = {
  projects: [...projects],
  departments: [...departments],
  positions: [...positions],
  workflowProgress,
  jobProfiles: [...jobProfiles],
  questionnaires: [...questionnaires],
  questionnaireTemplates,
};
