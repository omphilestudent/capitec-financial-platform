// Placeholder data for the Cases console. Nothing here is copied from
// reference screenshots. Replace with API calls once the backend exposes
// case endpoints.
//
// Field rows are [label, value] or [label, value, 'link' | 'check' | 'long'].
// Blank values render as an empty field.

export const CASE_STATUS_PATH = ['New', 'Assigned', 'In Progress', 'Waiting for External', 'Closed']

const SAMPLE_DESCRIPTION = [
  'Alert summary:',
  'Placeholder description for demonstration purposes.',
  '',
  'Rules triggered:',
  'RULE-A01, RULE-B02',
  '',
  'Supporting data:',
  'account_no=0000000001',
  'amount=-50000.00',
  'channel=sample',
  'score=0.00',
  'reviewed_by=analyst',
  'status=open',
  'source=demo',
].join('\n')

export const cases = [
  {
    id: 'CS-1001',
    title: 'Account activity review',
    priority: 'Critical',
    status: 'Follow Up',
    owner: 'Fraud Monitoring Team',
    client: 'Sample Client A',
    accountNumber: '0000000001',
    sidebar: 'risk',
    lastKyc: '01 Jan 2024',
    riskGrading: 'Medium',
    sections: [
      {
        title: 'Fields',
        fields: [
          ['Case Number', 'CS-1001', 'link'],
          ['Client Name', 'Sample Client A', 'client'],
          ['Case Origin', 'Monitoring'],
          ['Priority', 'Critical'],
          ['Contact Name', ''],
          ['Contact Phone', ''],
          ['Allow Case Owner Change', false, 'check'],
          ['Case Owner', 'Fraud Monitoring Team'],
          ['Status', 'Follow Up'],
          ['Sub Status', ''],
          ['Case Outcome', ''],
        ],
      },
      {
        title: 'Description Information',
        fields: [
          ['Subject', 'Account activity review'],
          ['Description', SAMPLE_DESCRIPTION, 'long'],
        ],
      },
      {
        title: 'Taxonomy',
        fields: [
          ['Type', ''], ['Case Category', ''],
          ['Case Sub Category', ''], ['Method of Detection', ''],
        ],
      },
      {
        title: 'Fraud Amounts',
        fields: [
          ['Total Amount Defrauded', ''], ['Total Loss Suffered For Organisation', ''],
          ['Total Loss Suffered For Client', ''], ['Amount Recovered Internal', ''],
        ],
      },
      {
        title: 'Resolution Details',
        fields: [
          ['Incident Date', '2026/01/10'], ['Resolution Confirmed', false, 'check'],
          ['Resolution Details', ''], ['Claim Outcome', ''],
        ],
      },
      {
        title: 'Additional Information',
        fields: [
          ['Case Record Type', 'Fraud'],
          ['Created By', 'Platform Service Account, 2026/01/10, 09:00'],
          ['Last Modified By', 'Analyst One, 2026/02/04, 14:30'],
          ['Date/Time Closed', ''],
        ],
      },
    ],
    frauds: [
      {
        id: 'FRA-1001', rule: 'RULE-A01, RULE-B02', amount: -50000, priority: 'High',
        ruleDescription: 'Example rule: new account with large outbound transfers',
        beneficiaryBank: 'Sample Bank', fromAccount: '0000000001', postDate: '2026/01/10',
        caseNumber: 'CS-1001', destinationAccount: '0000000099',
        journalNumber: '000001', transactionDate: '', createdBy: 'Platform Service Account, 2026/01/10, 09:00',
        lastModifiedBy: 'Platform Service Account, 2026/01/10, 09:00',
      },
      {
        id: 'FRA-1002', rule: 'RULE-A01, RULE-B02', amount: -50000, priority: 'High',
        ruleDescription: 'Example rule: new account with large outbound transfers',
        beneficiaryBank: 'Sample Bank', fromAccount: '0000000001', postDate: '2026/01/10',
        caseNumber: 'CS-1001', destinationAccount: '0000000099',
        journalNumber: '000002', transactionDate: '', createdBy: 'Platform Service Account, 2026/01/10, 09:00',
        lastModifiedBy: 'Platform Service Account, 2026/01/10, 09:00',
      },
      {
        id: 'FRA-1003', rule: 'RULE-A01, RULE-B02', amount: -50000, priority: 'High',
        ruleDescription: 'Example rule: new account with large outbound transfers',
        beneficiaryBank: 'Sample Bank', fromAccount: '0000000001', postDate: '2026/01/10',
        caseNumber: 'CS-1001', destinationAccount: '0000000099',
        journalNumber: '000003', transactionDate: '', createdBy: 'Platform Service Account, 2026/01/10, 09:00',
        lastModifiedBy: 'Platform Service Account, 2026/01/10, 09:00',
      },
    ],
    transactions: [
      { id: 'TXN-0001', date: '2026-01-10', time: '12:00', description: 'Transfer to beneficiary', classification: 'EFT', money: 'DEBIT', amount: -50000, balance: 1250.5 },
      { id: 'TXN-0002', date: '2026-01-10', time: '11:15', description: 'Transfer to beneficiary', classification: 'EFT', money: 'DEBIT', amount: -50000, balance: 51250.5 },
      { id: 'TXN-0003', date: '2026-01-09', time: '16:40', description: 'EFT deposit', classification: 'EFT', money: 'CREDIT', amount: 25000, balance: 101250.5 },
      { id: 'TXN-0004', date: '2026-01-08', time: '09:05', description: 'Scheduled payment', classification: 'RTC', money: 'DEBIT', amount: -1200, balance: 76250.5 },
    ],
    statements: [
      { period: 'December 2025', account: '0000000001', generated: '2026/01/02' },
      { period: 'November 2025', account: '0000000001', generated: '2025/12/02' },
    ],
    feed: [
      { who: 'Analyst One', when: '4 Feb 2026 at 14:30', kind: 'updated', text: 'Case updated', detail: 'Case Owner: Fraud Monitoring Team' },
      { who: 'Analyst Two', when: '13 Jan 2026 at 10:00', kind: 'status', text: 'Case status updated' },
      { who: 'Platform Service Account', when: '10 Jan 2026 at 09:00', kind: 'created', text: 'Case created' },
    ],
  },
  {
    id: 'CS-1002',
    title: 'Card dispute follow-up',
    priority: 'Low',
    status: 'New',
    owner: 'Dispute Team',
    client: 'Sample Client B',
    accountNumber: '0000000002',
    sidebar: 'sla',
    sections: [
      {
        title: 'Fields',
        fields: [
          ['Case Number', 'CS-1002', 'link'],
          ['Client Name', 'Sample Client B', 'client'],
          ['Case Origin', 'Operations'],
          ['Priority', 'Low'],
          ['Financial Account', '0000000002', 'link'],
          ['Case Owner', 'Dispute Team'],
          ['Status', 'New'],
          ['Case Outcome', ''],
        ],
      },
      {
        title: 'Description Information',
        fields: [
          ['Subject', 'Card dispute follow-up'],
          ['Description', 'Placeholder description for demonstration purposes.'],
        ],
      },
      {
        title: 'Taxonomy',
        fields: [
          ['Type', 'Dispute'], ['Case Category', 'Operational'],
          ['Case Sub Category', 'Process'], ['Method of Detection', 'Manual review'],
        ],
      },
      {
        title: 'Additional Information',
        fields: [
          ['Case Record Type', 'Dispute'],
          ['Created By', 'Analyst Three, 2026/03/01, 08:00'],
          ['Last Modified By', 'Analyst Four, 2026/03/20, 10:00'],
        ],
      },
      {
        title: 'Service Level',
        fields: [
          ['Elapsed Days', '0,00'], ['Due Date', ''],
          ['Breached', false, 'check'], ['Notification Sent', false, 'check'],
        ],
      },
    ],
    disputes: [
      { id: 'DSP-0001', trace: '000000000001', amount: 'R 5 000', date: '2026/02/12' },
      { id: 'DSP-0002', trace: '000000000002', amount: 'R 5 000', date: '2026/02/12' },
      { id: 'DSP-0003', trace: '000000000003', amount: 'R 5 000', date: '2026/02/12' },
    ],
    frauds: [],
    transactions: [
      { id: 'TXN-0010', date: '2026-02-12', time: '14:22', description: 'Card authorisation: Sample Merchant', classification: 'CARD AUTHORISATION', money: 'DEBIT', amount: -845.6, balance: 4210.4 },
    ],
    statements: [
      { period: 'February 2026', account: '0000000002', generated: '2026/03/02' },
    ],
    feed: [
      { who: 'Analyst Three', when: '5 Mar 2026 at 06:47', kind: 'updated', text: 'Case updated', detail: 'Case Owner: Dispute Team' },
      { who: 'Analyst Three', when: '4 Mar 2026 at 14:48', kind: 'updated', text: 'Case updated' },
    ],
  },
  {
    id: 'CS-1003',
    title: 'Closed dispute',
    priority: 'Low',
    status: 'Closed',
    owner: 'Dispute Team',
    client: 'Sample Client C',
    accountNumber: '0000000002',
    sidebar: 'sla',
    sections: [
      {
        title: 'Fields',
        fields: [
          ['Case Number', 'CS-1003', 'link'],
          ['Case Origin', 'Operations'],
          ['Priority', 'Low'],
          ['Case Owner', 'Dispute Team'],
          ['Status', 'Closed'],
          ['Case Outcome', ''],
        ],
      },
      {
        title: 'Description Information',
        fields: [['Subject', 'Closed dispute'], ['Description', '']],
      },
      {
        title: 'Additional Information',
        fields: [['Case Record Type', 'Dispute'], ['Date/Time Closed', '2026/04/30, 10:27']],
      },
    ],
    disputes: [
      { id: 'DSP-0004', trace: '000000000004', amount: 'R 50', date: '2026/03/30' },
    ],
    frauds: [],
    transactions: [],
    statements: [],
    feed: [
      { who: 'Analyst Three', when: '30 Apr 2026 at 10:27', kind: 'status', text: 'Case closed' },
      { who: 'Analyst Three', when: '30 Apr 2026 at 10:26', kind: 'status', text: 'Case status updated' },
    ],
  },
]

export const getCase = (id) => cases.find((item) => item.id === id)

/** Every fraud alert in the demo data, with the case it belongs to. */
export const allFrauds = cases.flatMap((c) => c.frauds.map((f) => ({ ...f, caseId: c.id })))

export const getFraud = (id) => allFrauds.find((f) => f.id === id)
